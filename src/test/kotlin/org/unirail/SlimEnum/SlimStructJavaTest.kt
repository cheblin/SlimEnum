package org.unirail.SlimEnum

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * SlimStruct in Java: a pack is a primitive with a type annotation, its accessors are static methods.
 * Both layouts of a struct are checked: the groups ( `Pack.get.field( pack )` ) and the former fields ( `Pack.field.Val.get( pack )` ).
 */
class SlimStructJavaTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // the light project has no JDK: the few classes the structs mention
        myFixture.addFileToProject("java/lang/Object.java", "package java.lang; public class Object {}")
        myFixture.addFileToProject("java/lang/String.java", "package java.lang; public final class String {}")
        myFixture.addFileToProject("java/lang/annotation/ElementType.java", "package java.lang.annotation; public enum ElementType { TYPE, FIELD, METHOD, PARAMETER, TYPE_USE }")
        myFixture.addFileToProject("java/lang/annotation/Target.java", "package java.lang.annotation; public @interface Target { ElementType[] value(); }")
    }

    private fun groups() = myFixture.addFileToProject(
        "IPv4.java",
        """
        import java.lang.annotation.*;

        @Target( ElementType.TYPE_USE )
        public @interface IPv4 {
            @IPv4 int EMPTY_PACK = 0;

            interface get {
                static int a( @IPv4 int pack ) { return pack >>> 24 & 0xFF; }
                static int d( @IPv4 int pack ) { return pack & 0xFF; }
            }

            interface set {
                static @IPv4 int a( @IPv4 int pack, int src ) { return pack & ~( 0xFF << 24 ) | ( src & 0xFF ) << 24; }
                static @IPv4 int d( @IPv4 int pack, int src ) { return pack & ~0xFF | src & 0xFF; }
            }

            interface New {
                static @IPv4 int of( int a, int d ) { return set.d( set.a( EMPTY_PACK, a ), d ); }
                static String str( @IPv4 int pack ) { return jso.n( pack ); }
            }

            interface jso {
                static String n( @IPv4 int pack ) { return null; }
            }

            @Target( ElementType.TYPE_USE )
            @interface Nullable {
                interface value {
                    static boolean hasValue( @Nullable long src ) { return src != NULL; }
                    static @IPv4 int get( @Nullable long src ) { return ( int ) src; }
                    static @Nullable long set( @IPv4 int src ) { return src; }
                    static @Nullable long to_null() { return NULL; }
                }
                @Nullable long NULL = 0x1_0000_0000L;
            }
        }
        """.trimIndent(),
    )

    private fun fields() = myFixture.addFileToProject(
        "Old.java",
        """
        import java.lang.annotation.*;

        @Target( ElementType.TYPE_USE )
        public @interface Old {
            @Target( ElementType.TYPE_USE )
            @interface a {
                interface Val {
                    static int get( @Old int src ) { return src >>> 24 & 0xFF; }
                    static @Old int set( int v, @Old int dst ) { return dst & ~( 0xFF << 24 ) | ( v & 0xFF ) << 24; }
                }
            }
        }
        """.trimIndent(),
    )

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
        myFixture.configureByText("Use.java", code)
        return myFixture.completeBasic().orEmpty().map { it.lookupString }
    }

    private fun pick(code: String, item: String) {
        myFixture.configureByText("Use.java", code)
        val items = myFixture.completeBasic() ?: error("nothing is offered")
        myFixture.lookup.currentItem = items.firstOrNull { it.lookupString == item } ?: error("$item is not offered: ${items.map { it.lookupString }}")
        myFixture.finishLookup('\n')
    }

    fun `test a pack offers the accessors of its groups`() {
        groups()
        val items = complete("class Use { void m() { @IPv4 int addr = 0; addr.<caret> } }")
        assertContainsElements(items, "get.a", "get.d", "set.a", "set.d", "New.of", "New.str", "jso.n")
        assertDoesntContain(items, "hashCode", "toString") // what Java offers by itself is put aside
    }

    fun `test a getter takes the pack`() {
        groups()
        pick("class Use { int m() { @IPv4 int addr = 0; return addr.<caret>; } }", "get.a")
        same("class Use { int m() { @IPv4 int addr = 0; return IPv4.get.a(addr); } }")
    }

    fun `test a setter is assigned back, the pack goes first`() {
        groups()
        pick("class Use { void m() { @IPv4 int addr = 0; addr.<caret> } }", "set.a")
        same("class Use { void m() { @IPv4 int addr = 0; addr = IPv4.set.a(addr, <caret>); } }")
    }

    fun `test a maker is assigned to the pack`() {
        groups()
        pick("class Use { void m() { @IPv4 int addr = 0; addr.<caret> } }", "New.of")
        same("class Use { void m() { @IPv4 int addr = 0; addr = IPv4.New.of(<caret>); } }")
    }

    fun `test the kind of a pack goes with the value`() {
        groups()
        val items = complete("class Use { void m() { @IPv4 int addr = 0; int copy = addr; copy.<caret> } }")
        assertContainsElements(items, "get.a", "set.d")
    }

    fun `test a slot of a pack offers what makes one`() {
        groups()
        val items = complete("class Use { void m() { @IPv4 int addr = <caret>; } }")
        assertContainsElements(items, "New.of", "EMPTY_PACK")
        assertDoesntContain(items, "set.a", "get.a", "New.str")
    }

    fun `test a maker in a slot`() {
        groups()
        pick("class Use { void m() { @IPv4 int addr = <caret>; } }", "New.of")
        same("class Use { void m() { @IPv4 int addr = IPv4.New.of(<caret>); } }")
    }

    fun `test the nullable form offers its own accessors only`() {
        groups()
        val items = complete("class Use { void m() { @IPv4.Nullable long maybe = 0; maybe.<caret> } }")
        assertContainsElements(items, "value.hasValue", "value.get")
        assertDoesntContain(items, "get.a", "set.a", "jso.n")
    }

    fun `test the former layout is understood`() {
        fields()
        assertContainsElements(complete("class Use { void m() { @Old int addr = 0; addr.<caret> } }"), "get", "set")
        pick("class Use { void m() { @Old int addr = 0; addr.<caret> } }", "set")
        same("class Use { void m() { @Old int addr = 0; addr = Old.a.Val.set(<caret>, addr); } }")
    }

    fun `test a pack is lifted to its nullable form`() {
        groups()
        pick("class Use { long m() { @IPv4 int addr = 0; return addr.<caret>; } }", "set")
        same("class Use { long m() { @IPv4 int addr = 0; return IPv4.Nullable.value.set(addr); } }")
    }

    fun `test the accessor of the nullable form`() {
        groups()
        pick("class Use { boolean m() { @IPv4.Nullable long maybe = 0; return maybe.<caret>; } }", "value.hasValue")
        same("class Use { boolean m() { @IPv4.Nullable long maybe = 0; return IPv4.Nullable.value.hasValue(maybe); } }")
    }

    fun `test the name of a field finds its accessors`() {
        groups()
        val items = complete("class Use { void m() { @IPv4 int addr = 0; addr.d<caret> } }")
        assertContainsElements(items, "get.d", "set.d")
        assertDoesntContain(items, "get.a", "set.a")
    }

    fun `test what a method returns and what it takes`() {
        groups()
        assertContainsElements(complete("class Use { @IPv4 int m() { return <caret>; } }"), "New.of", "EMPTY_PACK")
        assertContainsElements(complete("class Use { void send( @IPv4 int to ) {} void m() { send(<caret>); } }"), "New.of", "EMPTY_PACK")
        assertContainsElements(complete("class Use { void m() { @IPv4 int addr = 0; addr = <caret>; } }"), "New.of", "EMPTY_PACK")
    }

    fun `test a slot of a pack keeps what Java offers`() {
        groups()
        assertContainsElements(complete("class Use { void m() { @IPv4 int other = 0; @IPv4 int addr = <caret>; } }"), "New.of", "other")
    }
}
