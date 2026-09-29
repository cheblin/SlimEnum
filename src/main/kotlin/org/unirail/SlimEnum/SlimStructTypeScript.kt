package org.unirail.SlimEnum

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.javascript.psi.JSAssignmentExpression
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSConditionalExpression
import com.intellij.lang.javascript.psi.JSDefinitionExpression
import com.intellij.lang.javascript.psi.JSExpressionStatement
import com.intellij.lang.javascript.psi.JSFieldVariable
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSIndexedPropertyAccessExpression
import com.intellij.lang.javascript.psi.JSInitializerOwner
import com.intellij.lang.javascript.psi.JSParenthesizedExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.JSReturnStatement
import com.intellij.lang.javascript.psi.JSTypeDeclarationOwner
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.lang.javascript.psi.ecma6.TypeScriptArrayType
import com.intellij.lang.javascript.psi.ecma6.TypeScriptCastExpression
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass
import com.intellij.lang.javascript.psi.ecma6.TypeScriptModule
import com.intellij.lang.javascript.psi.ecma6.TypeScriptSingleType
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.lang.javascript.psi.ecma6.TypeScriptUnionOrIntersectionType
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.ecmal4.JSQualifiedNamedElement
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil

/**
 * SlimStruct in TypeScript. A pack is a `number` ( a `bigint`, an object of a class ) under a name of its own, its
 * accessors are the functions of the namespace of that name, gathered in groups by what they do - the pack first, the
 * value second:
 *
 *     export type Pack = number
 *     export namespace Pack {
 *         export namespace get      { export function f( pack: Pack ): number }
 *         export namespace set      { export function f( pack: Pack, src: number ): Pack }
 *         export namespace hasValue { export function f( pack: Pack ): boolean }
 *         export namespace to_null  { export function f( pack: Pack ): Pack }
 *         export namespace New      { export function of( f: number, ... ): Pack; export function str( pack: Pack ): string }
 *         export namespace jso      { export function n( pack: Pack ): string; export function o( pack: Pack ) }
 *         export const EMPTY_PACK = <Pack>0
 *
 *         export type Nullable = Pack | Nullable.NULL
 *         export namespace Nullable { export function get( src: Nullable ): Pack ... }
 *     }
 *
 * `pack.` offers them, and `Pack.get.f( pack )` is written; what returns a pack is assigned back: `pack = Pack.set.f( pack, 1 )`.
 * A slot that takes a pack offers what makes one: `Pack.New.of( … )`, `Pack.EMPTY_PACK`.
 *
 * A number knows nothing of the name it is declared under, so the name is taken the way the Java side takes the
 * annotation: from the declaration of what holds the pack, from what the pack is made by, from what is assigned to it.
 */
class SlimStructTypeScript : CompletionContributor() {

    /**
     * @param type   the name a pack is declared under: `type Pack = number`, `class Pack`
     * @param spaces the namespaces of that name: TypeScript merges them with the type
     * @param hint   the way to the pack as it is written where the pack was found: `FW.ListRequest`
     */
    private class Pack(val type: PsiNamedElement, val spaces: List<TypeScriptModule>, val hint: String?) {
        val name: String get() = type.name.orEmpty()

        /** `found` is the type of the pack or a namespace of it. */
        fun holds(found: PsiElement?): Boolean = same(found, type) || spaces.any { same(found, it) }
    }

    private companion object {
        /**
         * One declaration. While completion runs the file is a copy of itself: a declaration is met as it is in the file
         * and as it is in the copy, and the two are not equal as objects.
         */
        fun same(a: PsiElement?, b: PsiElement?): Boolean {
            if (a == null || b == null) return false
            if (a == b) return true
            if (a.javaClass != b.javaClass || a.containingFile?.originalFile != b.containingFile?.originalFile) return false
            return a is JSQualifiedNamedElement && b is JSQualifiedNamedElement && a.qualifiedName != null && a.qualifiedName == b.qualifiedName
        }
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return
        val position = parameters.position
        val ref = position.parent as? JSReferenceExpression ?: return

        val qualifier = ref.qualifier
        if (qualifier != null) {
            val pack = packOf(qualifier, mutableSetOf()) ?: return
            // an object of a class has its fields: what TypeScript offers by itself stays
            if (offerAccessors(pack, qualifier, position, result) && pack.type !is TypeScriptClass) result.stopHere()
            return
        }

        val pack = expected(ref) ?: return
        offerMakers(pack, position, result) // what else fits the slot stays
    }

    // ================================================================ what is a pack

    private fun packOfType(type: PsiElement?, depth: Int = 0): Pack? = when (type) {
        is TypeScriptSingleType -> named(type)?.let { packOfName(it) }
        is TypeScriptUnionOrIntersectionType -> if (4 < depth) null else type.types.firstNotNullOfOrNull { packOfType(it, depth + 1) } // `Pack | undefined`
        else -> null
    }

    /** The name a type is written with: `FW.ListRequest` of `let request: FW.ListRequest`. */
    private fun named(type: TypeScriptSingleType): JSReferenceExpression? = PsiTreeUtil.getChildOfType(type, JSReferenceExpression::class.java)

    /** The last part of a name: `ListRequest` of `FW.ListRequest`. */
    private fun last(name: JSReferenceExpression): String? = name.referenceNameElement?.text

    /** `name` is a reference to a pack: in a type - `FW.ListRequest`, in an expression - the head of `FW.ListRequest.New.of( … )`. */
    private fun packOfName(name: JSReferenceExpression): Pack? {
        val found = name.multiResolve(false).mapNotNull { it.element }
        val spaces = LinkedHashSet<TypeScriptModule>()
        found.filterIsInstanceTo(spaces)
        val type = found.firstOrNull { it is TypeScriptTypeAlias || it is TypeScriptClass } as? PsiNamedElement
            ?: spaces.firstOrNull()?.let { beside(it).firstOrNull { t -> t.name == it.name } }
            ?: return null

        val home = owner(type)
        val around = if (home is TypeScriptModule) members(home, TypeScriptModule::class.java) else type.containingFile?.let { PsiTreeUtil.findChildrenOfType(it, TypeScriptModule::class.java).filter { m -> owner(m) == null } }.orEmpty()
        around.filterTo(spaces) { it.name == type.name }

        if (spaces.none { groups(it).isNotEmpty() || functions(it).isNotEmpty() }) return null
        return Pack(type, spaces.toList(), name.text)
    }

    /** What holds `e`: a namespace, a function, a class; null - the file. */
    private fun owner(e: PsiElement): PsiElement? =
        PsiTreeUtil.getParentOfType(e, TypeScriptModule::class.java, JSFunction::class.java, JSClass::class.java)

    private fun <T : PsiElement> members(space: TypeScriptModule, kind: Class<T>): List<T> =
        PsiTreeUtil.findChildrenOfType(space, kind).filter { owner(it) === space }

    /** The types declared where the namespace is. */
    private fun beside(space: TypeScriptModule): List<JSClass> {
        val home = owner(space)
        val all = if (home is TypeScriptModule) members(home, JSClass::class.java)
        else PsiTreeUtil.findChildrenOfType(space.containingFile, JSClass::class.java).filter { owner(it) == null }
        return all.filter { it is TypeScriptTypeAlias || it is TypeScriptClass }
    }

    private fun functions(space: TypeScriptModule): List<JSFunction> = members(space, JSFunction::class.java).filter { it.name != null }

    /**
     * The groups of a pack: the namespaces inside that hold functions. A namespace of a type declared beside it
     * is not a group - it is a form of the pack, `Nullable`, with a name and accessors of its own.
     */
    private fun groups(space: TypeScriptModule): List<TypeScriptModule> {
        val forms = members(space, JSClass::class.java).mapNotNullTo(HashSet()) { it.name }
        return members(space, TypeScriptModule::class.java).filter { it.name !in forms && functions(it).isNotEmpty() }
    }

    /** The forms of a pack: the namespaces inside that go with a type of their own - `Nullable`. */
    private fun forms(space: TypeScriptModule): List<TypeScriptModule> {
        val types = members(space, JSClass::class.java).mapNotNullTo(HashSet()) { it.name }
        return members(space, TypeScriptModule::class.java).filter { it.name in types && functions(it).isNotEmpty() }
    }

    private fun refers(type: PsiElement?, pack: Pack, depth: Int = 0): Boolean = when (type) {
        is TypeScriptSingleType -> {
            val name = named(type)
            name != null && last(name) == pack.name && name.multiResolve(false).mapNotNull { it.element }.let { found ->
                found.isEmpty() || found.any { pack.holds(it) }
            }
        }
        else -> false
    }

    // ================================================================ the pack of an expression

    private fun packOf(expr: PsiElement?, seen: MutableSet<PsiElement>): Pack? = when (expr) {
        null -> null
        is JSParenthesizedExpression -> packOf(expr.innerExpression, seen)
        is TypeScriptCastExpression -> packOfType(expr.type) ?: packOf(expr.expression, seen) // `<Pack> x`, `x as Pack`
        is JSConditionalExpression -> packOf(expr.thenBranch, seen) ?: packOf(expr.elseBranch, seen)
        is JSCallExpression -> made(expr)
        is JSIndexedPropertyAccessExpression -> item(expr.qualifier)
        is JSReferenceExpression -> when (val target = expr.resolve()) {
            is JSFieldVariable -> packOfVariable(target, seen)
            else -> null
        }
        else -> null
    }

    /** `Pack.set.f( pack, 1 )`: the function says what it returns, the head of the call says how the pack is reached from here. */
    private fun made(call: JSCallExpression): Pack? {
        val method = call.methodExpression as? JSReferenceExpression ?: return null
        val function = method.resolve() as? JSFunction ?: return null
        val pack = packOfType(function.returnTypeElement) ?: return null

        var head = method.qualifier
        while (head is JSReferenceExpression) {
            if (names(head, pack)) return Pack(pack.type, pack.spaces, head.text)
            head = head.qualifier
        }
        return Pack(pack.type, pack.spaces, if (function.containingFile?.originalFile == call.containingFile?.originalFile) pack.hint else null)
    }

    /**
     * `name` stands for the pack: the pack itself, or a constant that holds it - `const C = Protocol.Testers.Command`,
     * the usual way to keep a long name short.
     */
    private fun names(name: JSReferenceExpression, pack: Pack, depth: Int = 0): Boolean =
        name.multiResolve(false).any {
            val found = it.element
            pack.holds(found) ||
                depth < 4 && found is JSVariable && found.isConst && (found.initializer as? JSReferenceExpression)?.let { held -> names(held, pack, depth + 1) } == true
        }

    /** `packs[ i ]`: what the array is declared of. */
    private fun item(array: PsiElement?): Pack? {
        val declared = (array as? JSReferenceExpression)?.resolve() as? JSTypeDeclarationOwner ?: return null
        val of = (declared.typeElement as? TypeScriptArrayType)?.type ?: return null
        return packOfType(of)?.let { here(it, declared, array) }
    }

    /** The hint of a pack is good where the pack was found: in another file there is none. */
    private fun here(pack: Pack, found: PsiElement, site: PsiElement): Pack =
        if (found.containingFile?.originalFile == site.containingFile?.originalFile) pack else Pack(pack.type, pack.spaces, null)

    private fun packOfVariable(variable: JSFieldVariable, seen: MutableSet<PsiElement>): Pack? {
        if (!seen.add(variable)) return null

        packOfType(variable.typeElement)?.let { return it }
        (variable as? JSInitializerOwner)?.initializer?.let { packOf(it, seen) }?.let { return it }

        // what is assigned to it later: for a local in its function, for the rest in its file
        val scope: PsiElement = (if (variable is JSVariable && variable.isLocal) PsiTreeUtil.getParentOfType(variable, JSFunction::class.java) else null)
            ?: variable.containingFile ?: return null
        for (use in ReferencesSearch.search(variable, LocalSearchScope(scope)).findAll()) {
            val definition = use.element.parent as? JSDefinitionExpression ?: continue
            val assignment = definition.parent as? JSAssignmentExpression ?: continue
            packOf(assignment.rOperand, seen)?.let { return it }
        }
        return null
    }

    // ================================================================ a slot that takes a pack

    private fun expected(ref: JSReferenceExpression): Pack? {
        var value: PsiElement = ref
        while (value.parent is JSParenthesizedExpression) value = value.parent

        return when (val slot = value.parent) {
            is JSFieldVariable -> if ((slot as? JSInitializerOwner)?.initializer === value) packOfType(slot.typeElement) else null
            is JSAssignmentExpression -> if (slot.rOperand === value) packOf(slot.definitionExpression?.expression, mutableSetOf()) else null
            is JSReturnStatement -> packOfType(PsiTreeUtil.getParentOfType(slot, JSFunction::class.java)?.returnTypeElement)
            else -> {
                val call = slot?.parent as? JSCallExpression ?: return null
                val at = call.arguments.indexOfFirst { it === value }
                val function = (call.methodExpression as? JSReferenceExpression)?.resolve() as? JSFunction ?: return null
                val parameter = function.parameterVariables.getOrNull(at) ?: return null
                packOfType(parameter.typeElement)?.let { here(it, function, call) }
            }
        }
    }

    // ================================================================ how a pack is written here

    private fun written(pack: Pack, site: PsiElement): String {
        val file = site.containingFile?.originalFile
        val ways = LinkedHashSet<String>()
        pack.hint?.let { ways.add(it) }
        // the way the file reaches the pack already
        if (file != null) for (name in PsiTreeUtil.findChildrenOfType(file, JSReferenceExpression::class.java)) {
            if (last(name) != pack.name || name.textLength > 200) continue
            if (names(name, pack)) ways.add(name.text)
        }
        (pack.type as? JSQualifiedNamedElement)?.qualifiedName?.let { ways.add(it) }

        return ways.firstOrNull { reaches(it, site, pack) } ?: ways.firstOrNull() ?: pack.name
    }

    private fun reaches(way: String, site: PsiElement, pack: Pack): Boolean = try {
        (JSPsiElementFactory.createJSExpression(way, site) as? JSReferenceExpression)?.let { names(it, pack) } == true
    } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
        throw e
    } catch (_: Exception) {
        false
    }

    // ================================================================ what is offered

    private fun offerAccessors(pack: Pack, qualifier: PsiElement, position: PsiElement, result: CompletionResultSet): Boolean {
        val way by lazy { written(pack, position) }
        var offered = false

        fun offer(function: JSFunction, group: String?, takes: Boolean = false) {
            val name = function.name ?: return
            val parameters = function.parameterVariables
            val at = parameters.indexOfFirst { refers(it.typeElement, pack) }
            val returns = refers(function.returnTypeElement, pack)
            if (at < 0 && (takes || !returns)) return // neither of the pack nor makes one

            val path = if (group == null) name else "$group.$name"
            val arguments = parameters.mapIndexed { i, _ -> if (i == at) qualifier.text else "" }
            result.addElement(
                LookupElementBuilder.create(function, path)
                    .withLookupString(name) // the name of the field alone finds every accessor of the field
                    .withIcon(function.getIcon(0))
                    .withTypeText(function.returnTypeElement?.text.orEmpty())
                    .withTailText(signature(function), true)
                    .withInsertHandler { context, _ -> insertCall(context, "$way.$path(", arguments, returns) },
            )
            offered = true
        }

        for (space in pack.spaces) {
            for (group in groups(space)) for (function in functions(group)) offer(function, group.name)
            for (function in functions(space)) offer(function, null) // a form of a pack: `Pack.Nullable.get( src )`
            for (form in forms(space)) for (function in functions(form)) offer(function, form.name, takes = true) // the pack into its form: `Pack.Nullable.set( pack )`
        }
        return offered
    }

    private fun offerMakers(pack: Pack, position: PsiElement, result: CompletionResultSet): Boolean {
        val way by lazy { written(pack, position) }
        var offered = false

        for (space in pack.spaces) {
            for (group in groups(space)) for (function in functions(group)) {
                val name = function.name ?: continue
                val parameters = function.parameterVariables
                if (!refers(function.returnTypeElement, pack) || parameters.any { refers(it.typeElement, pack) }) continue // changes a pack, makes none
                result.addElement(
                    LookupElementBuilder.create(function, "${group.name}.$name")
                        .withLookupString(name)
                        .withLookupString(pack.name)
                        .withIcon(function.getIcon(0))
                        .withTypeText(pack.name)
                        .withTailText(signature(function), true)
                        .withInsertHandler { context, _ -> insertValue(context, "$way.${group.name}.$name()", if (parameters.isEmpty()) 0 else 1) },
                )
                offered = true
            }

            for (constant in members(space, JSVariable::class.java)) {
                val name = constant.name ?: continue
                if (!constant.isConst) continue
                val of = packOfType(constant.typeElement) ?: packOf(constant.initializer, mutableSetOf()) ?: continue
                if (!same(of.type, pack.type)) continue
                result.addElement(
                    LookupElementBuilder.create(constant, name)
                        .withLookupString(pack.name)
                        .withIcon(constant.getIcon(0))
                        .withTypeText(pack.name)
                        .withInsertHandler { context, _ -> insertValue(context, "$way.$name", 0) },
                )
                offered = true
            }
        }
        return offered
    }

    private fun signature(function: JSFunction): String =
        function.parameterVariables.joinToString(", ", "(", ")") { p -> p.typeElement?.text?.let { "${p.name}: $it" } ?: p.name.orEmpty() }

    // ================================================================ what is written

    /**
     * `pack.get.f` typed and chosen becomes `Pack.get.f( pack )`. What returns a pack, chosen where a statement stands
     * and the pack can be assigned to, becomes `pack = Pack.set.f( pack, <caret> )`.
     */
    private fun insertCall(context: InsertionContext, head: String, arguments: List<String>, returns: Boolean) {
        context.commitDocument()
        val file: PsiFile = context.file
        val first = PsiTreeUtil.getParentOfType(file.findElementAt(context.startOffset), JSReferenceExpression::class.java, false) ?: return
        val qualifier = first.qualifier ?: return
        val pack = qualifier.text

        // `pack.get.f`: the reference that holds all that was typed
        var whole: PsiElement = first
        while (whole.parent is JSReferenceExpression && whole.parent.textRange.endOffset <= context.tailOffset) whole = whole.parent
        var stands: PsiElement = whole
        while (stands.parent is JSParenthesizedExpression) stands = stands.parent
        val statement = stands.parent as? JSExpressionStatement

        val assignable = ((qualifier as? JSReferenceExpression)?.resolve() as? JSFieldVariable)?.let { !it.isConst } == true
        // nothing of the pack among the arguments: the function makes a pack, the brackets are left empty
        val made = arguments.all { it.isEmpty() }
        val call = head + (if (made) "" else arguments.joinToString(", ") { if (it.isEmpty()) "" else pack }) + ")"

        val from: Int
        val to: Int
        val text: String
        val before: Int // what stands before the arguments
        if (returns && assignable && statement != null) {
            from = stands.textRange.startOffset
            to = maxOf(stands.textRange.endOffset, context.tailOffset)
            text = "$pack = $call"
            before = pack.length + 3 + head.length
        } else {
            from = qualifier.textRange.startOffset
            to = context.tailOffset
            text = call
            before = head.length
        }
        context.document.replaceString(from, to, text)
        context.commitDocument()

        val empty = arguments.indexOfFirst { it.isEmpty() }
        val caret = if (empty < 0) from + text.length
        else from + before + arguments.take(empty).sumOf { (if (it.isEmpty()) 0 else pack.length) + 2 }
        context.editor.caretModel.moveToOffset(caret)
    }

    /** Puts `text` in place of what was typed; the caret goes `back` characters before its end - inside the brackets. */
    private fun insertValue(context: InsertionContext, text: String, back: Int) {
        context.commitDocument()
        val from = context.startOffset
        context.document.replaceString(from, context.tailOffset, text)
        context.commitDocument()
        context.editor.caretModel.moveToOffset(from + text.length - back)
    }
}
