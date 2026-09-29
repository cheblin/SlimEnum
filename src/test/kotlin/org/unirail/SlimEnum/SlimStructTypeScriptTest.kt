package org.unirail.SlimEnum

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * SlimStruct in TypeScript: a pack is a number under a name of its own, its accessors are the functions of the groups -
 * the namespaces inside the namespace of that name.
 */
class SlimStructTypeScriptTest : BasePlatformTestCase() {

    private val pack = """
        export type IPv4 = number;
        export namespace IPv4 {
            export namespace a__ { export const MIN = 0; export const MAX = 255; }

            export namespace get {
                export function a(pack: IPv4): number { return pack >>> 24 & 0xFF }
                export function d(pack: IPv4): number { return pack & 0xFF }
            }
            export namespace set {
                export function a(pack: IPv4, src: number): IPv4 { return pack & ~(0xFF << 24) | (src & 0xFF) << 24 }
                export function d(pack: IPv4, src: number): IPv4 { return pack & ~0xFF | src & 0xFF }
            }
            export namespace New {
                export function of(a: number, d: number): IPv4 { return set.d(set.a(EMPTY_PACK, a), d) }
                export function str(pack: IPv4): string { return jso.n(pack) }
            }
            export namespace jso {
                export function n(pack: IPv4): string { return JSON.stringify(o(pack)) }
                export function o(pack: IPv4) { return { a: get.a(pack), d: get.d(pack) } }
            }

            export const EMPTY_PACK = <IPv4>0;

            export type Nullable = IPv4 | Nullable.NULL;
            export namespace Nullable {
                export const NULL = 0x1_0000_0000;
                export type  NULL = 0x1_0000_0000;
                export function get(src: Nullable): IPv4 { return <IPv4>src; }
                export function set(src: IPv4): Nullable { return <Nullable>src; }
                export function hasValue(pack: Nullable): boolean { return pack !== NULL; }
                export function to_null() { return NULL; }
            }
        }

        export class Wide { a = 0; }
        export namespace Wide {
            export namespace get { export function a(pack: Wide): number { return pack.a } }
            export namespace set { export function a(pack: Wide, src: number): Wide { pack.a = src; return pack } }
        }

        export type Seconds = number;
        export namespace Seconds { export const MAX = 86400; }
    """.trimIndent()

    /** What the editor holds, with the caret marked. */
    private fun shown(): String {
        val text = myFixture.editor.document.text
        val at = myFixture.editor.caretModel.offset
        return text.substring(0, at) + "<caret>" + text.substring(at)
    }

    /** `expected` without a caret mark: the caret stands at the end of what was written, it is not checked. */
    private fun same(expected: String) =
        if (expected.contains("<caret>")) assertEquals(expected, shown()) else assertEquals(expected, myFixture.editor.document.text)

    private fun complete(code: String): List<String> {
        myFixture.configureByText("use.ts", "$pack\n$code")
        return myFixture.completeBasic().orEmpty().map { it.lookupString }
    }

    private fun pick(code: String, item: String, result: String) {
        myFixture.configureByText("use.ts", "$pack\n$code")
        val items = myFixture.completeBasic() ?: error("nothing is offered")
        myFixture.lookup.currentItem = items.firstOrNull { it.lookupString == item } ?: error("$item is not offered: ${items.map { it.lookupString }}")
        myFixture.finishLookup('\n')
        same("$pack\n$result")
    }

    fun `test a pack offers the accessors of its groups`() {
        val items = complete("function m() { let addr: IPv4 = 0; addr.<caret> }")
        assertContainsElements(items, "get.a", "get.d", "set.a", "set.d", "New.of", "New.str", "jso.n", "jso.o")
        assertDoesntContain(items, "toFixed", "toString") // what a number offers by itself is put aside
        assertDoesntContain(items, "get", "hasValue", "to_null") // the accessors of the nullable form are not of the pack
    }

    fun `test a getter takes the pack`() {
        pick("function m() { let addr: IPv4 = 0; return addr.<caret> }", "get.a", "function m() { let addr: IPv4 = 0; return IPv4.get.a(addr) }")
    }

    fun `test a setter is assigned back, the pack goes first`() {
        pick("function m() { let addr: IPv4 = 0; addr.<caret> }", "set.a", "function m() { let addr: IPv4 = 0; addr = IPv4.set.a(addr, <caret>) }")
    }

    fun `test a setter of a constant is not assigned`() {
        pick("function m() { const addr: IPv4 = 0; addr.<caret> }", "set.a", "function m() { const addr: IPv4 = 0; IPv4.set.a(addr, <caret>) }")
    }

    fun `test the name of a pack goes with what makes it`() {
        val items = complete("function m() { let addr = IPv4.New.of(1, 2); addr.<caret> }")
        assertContainsElements(items, "get.a", "set.d")
    }

    fun `test the name of a pack goes with what is assigned`() {
        val items = complete("function m() { let addr; addr = IPv4.set.a(IPv4.EMPTY_PACK, 1); addr.<caret> }")
        assertContainsElements(items, "get.a", "set.d")
    }

    fun `test the pack is written the way the file reaches it`() {
        pick(
            "const Address = IPv4; function m() { let addr = Address.New.of(1, 2); return addr.<caret> }", "get.d",
            "const Address = IPv4; function m() { let addr = Address.New.of(1, 2); return Address.get.d(addr) }",
        )
    }

    fun `test a parameter and an optional pack`() {
        assertContainsElements(complete("function m(addr: IPv4 | undefined) { addr.<caret> }"), "get.a", "set.d")
    }

    fun `test an item of an array of packs`() {
        assertContainsElements(complete("function m(all: IPv4[]) { all[0].<caret> }"), "get.a", "set.d")
    }

    fun `test a slot of a pack offers what makes one`() {
        val items = complete("function m() { let addr: IPv4 = <caret> }")
        assertContainsElements(items, "New.of", "EMPTY_PACK")
        assertDoesntContain(items, "set.a", "get.a", "New.str")
    }

    fun `test a maker in a slot`() {
        pick("function m() { let addr: IPv4 = <caret> }", "New.of", "function m() { let addr: IPv4 = IPv4.New.of(<caret>) }")
    }

    fun `test the nullable form offers its own accessors only`() {
        val items = complete("function m() { let maybe: IPv4.Nullable = 0; maybe.<caret> }")
        assertContainsElements(items, "hasValue", "get")
        assertDoesntContain(items, "get.a", "set.a", "jso.n")
    }

    fun `test the nullable form is written in full`() {
        pick("function m() { let maybe: IPv4.Nullable = 0; return maybe.<caret> }", "hasValue", "function m() { let maybe: IPv4.Nullable = 0; return IPv4.Nullable.hasValue(maybe) }")
    }

    fun `test a pack that is an object keeps its fields`() {
        val items = complete("function m(wide: Wide) { wide.<caret> }")
        assertContainsElements(items, "get.a", "set.a", "a")
    }

    fun `test a number under a name without accessors is left alone`() {
        val items = complete("function m(at: Seconds) { at.<caret> }")
        assertDoesntContain(items, "MAX")
    }

    fun `test a maker is assigned to the pack`() {
        pick("function m() { let addr: IPv4 = 0; addr.<caret> }", "New.of", "function m() { let addr: IPv4 = 0; addr = IPv4.New.of(<caret>) }")
    }

    fun `test a pack is lifted to its nullable form`() {
        val items = complete("function m() { let addr: IPv4 = 0; addr.<caret> }")
        assertContainsElements(items, "Nullable.set")
    }

    fun `test a cast names the pack`() {
        assertContainsElements(complete("function m(x: number) { let a = <IPv4>x; a.<caret> }"), "get.a", "set.d")
        assertContainsElements(complete("function m(x: number) { let a = x as IPv4; a.<caret> }"), "get.a", "set.d")
        assertContainsElements(complete("function m(x: number) { (x as IPv4).<caret> }"), "get.a", "set.d")
    }

    fun `test a field of a class and a property of an interface`() {
        assertContainsElements(complete("class Host { addr: IPv4 = 0; m() { this.addr.<caret> } }"), "get.a", "set.d")
        assertContainsElements(complete("interface Host { addr: IPv4 } function m(host: Host) { host.addr.<caret> }"), "get.a", "set.d")
    }

    fun `test a setter of a field is assigned to the field`() {
        pick(
            "interface Host { addr: IPv4 } function m(host: Host) { host.addr.<caret> }", "set.d",
            "interface Host { addr: IPv4 } function m(host: Host) { host.addr = IPv4.set.d(host.addr, <caret>) }",
        )
    }

    fun `test the name of a field finds its accessors`() {
        val items = complete("function m() { let addr: IPv4 = 0; addr.d<caret> }")
        assertContainsElements(items, "get.d", "set.d")
        assertDoesntContain(items, "get.a", "set.a")
    }

    fun `test what a function returns and what it takes`() {
        assertContainsElements(complete("function m(): IPv4 { return <caret> }"), "New.of", "EMPTY_PACK")
        assertContainsElements(complete("function send(to: IPv4) {} function m() { send(<caret>) }"), "New.of", "EMPTY_PACK")
        assertContainsElements(complete("function m() { let addr: IPv4 = 0; addr = <caret> }"), "New.of", "EMPTY_PACK")
    }

    /** The pack in a file of its own, as generated code is; a file with JSX cannot hold it: there `<IPv4>0` is a tag. */
    private fun imported(file: String, code: String, item: String): String {
        myFixture.addFileToProject("pack.ts", pack)
        myFixture.configureByText(file, "import { IPv4 } from \"./pack\"\n$code")
        val items = myFixture.completeBasic() ?: error("nothing is offered")
        myFixture.lookup.currentItem = items.firstOrNull { it.lookupString == item } ?: error("$item is not offered: ${items.map { it.lookupString }}")
        myFixture.finishLookup('\n')
        return myFixture.editor.document.text.substringAfter('\n')
    }

    fun `test a pack of another file`() {
        assertEquals(
            "function m() { let addr: IPv4 = 0; return IPv4.get.a(addr) }",
            imported("use.ts", "function m() { let addr: IPv4 = 0; return addr.<caret> }", "get.a"),
        )
    }

    fun `test a file with JSX`() {
        assertEquals(
            "function m() { let addr: IPv4 = 0; return IPv4.get.a(addr) }",
            imported("use.tsx", "function m() { let addr: IPv4 = 0; return addr.<caret> }", "get.a"),
        )
    }
}
