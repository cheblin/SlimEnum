package org.unirail.SlimEnum

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayAccessExpression
import com.intellij.psi.PsiArrayInitializerExpression
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiBinaryExpression
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiConditionalExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiPolyadicExpression
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.PsiStatement
import com.intellij.psi.PsiSwitchBlock
import com.intellij.psi.PsiSwitchLabelStatementBase
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiVariable
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil

class SlimEnumCompletion : CompletionContributor(), DumbAware {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return

        if (trySlimStruct(parameters, result)) {
            result.stopHere()
            return
        }

        val ctx = findEnumContext(parameters.position) ?: return
        val added = addSlimEnumCompletions(ctx, result)
        if (added) result.stopHere()
    }

    // ================================================================
    // SlimStruct: dot-access completion on `@<Struct> <prim>` variables
    // ================================================================

    private fun trySlimStruct(parameters: CompletionParameters, result: CompletionResultSet): Boolean {
        val position = parameters.position
        val ref = position.parent as? PsiReferenceExpression ?: return false
        val qualifier = ref.qualifierExpression ?: return false
        val packAnnotation = inferStructAnnotation(qualifier, mutableSetOf()) ?: return false
        val rootStruct = findRootStruct(packAnnotation) ?: return false
        val packType = qualifier.type ?: return false

        return emitStructAccessors(rootStruct, packAnnotation, packType, qualifier.text, result)
    }

    private fun isSlimStructClass(cls: PsiClass): Boolean {
        if (!cls.isAnnotationType) return false
        return cls.innerClasses.any { it.isAnnotationType }
    }

    private fun findRootStruct(ann: PsiClass): PsiClass? {
        var c: PsiClass? = ann
        while (c != null) {
            if (isSlimStructClass(c)) return c
            c = c.containingClass
        }
        return null
    }

    private fun allAnnotations(owner: PsiModifierListOwner): List<PsiAnnotation> {
        val out = mutableListOf<PsiAnnotation>()
        out.addAll(owner.annotations)
        when (owner) {
            is PsiVariable -> out.addAll(owner.type.annotations)
            is PsiMethod -> owner.returnType?.let { out.addAll(it.annotations) }
        }
        return out
    }

    private fun directStructAnnotation(owner: PsiModifierListOwner): PsiClass? {
        for (a in allAnnotations(owner)) {
            val cls = a.nameReferenceElement?.resolve() as? PsiClass ?: continue
            if (cls.isAnnotationType && findRootStruct(cls) != null) return cls
        }
        return null
    }

    private fun effectiveStructAnnotation(variable: PsiVariable, visited: MutableSet<PsiElement>): PsiClass? {
        if (!visited.add(variable)) return null

        directStructAnnotation(variable)?.let { return it }

        variable.initializer?.let { init ->
            inferStructAnnotation(init, visited)?.let { return it }
        }

        // Walk reassignments in the enclosing method for locals; for fields, project-wide.
        val scopeElement: PsiElement? = when (variable) {
            is PsiLocalVariable -> PsiTreeUtil.getParentOfType(variable, PsiMethod::class.java)
            is PsiField -> variable.containingFile
            else -> null
        }
        if (scopeElement != null) {
            val scope = LocalSearchScope(scopeElement)
            for (r in ReferencesSearch.search(variable, scope).findAll()) {
                val el = r.element
                val assignment = PsiTreeUtil.getParentOfType(el, PsiAssignmentExpression::class.java) ?: continue
                if (assignment.lExpression !== el) continue
                val rhs = assignment.rExpression ?: continue
                inferStructAnnotation(rhs, visited)?.let { return it }
            }
        }
        return null
    }

    private fun inferStructAnnotation(expr: PsiExpression, visited: MutableSet<PsiElement>): PsiClass? {
        return when (expr) {
            is PsiReferenceExpression -> when (val t = expr.resolve()) {
                is PsiVariable -> effectiveStructAnnotation(t, visited)
                is PsiMethod -> directStructAnnotation(t)
                else -> null
            }
            is PsiMethodCallExpression -> {
                val m = expr.resolveMethod() ?: return null
                directStructAnnotation(m)
            }
            is PsiTypeCastExpression -> {
                // First honour the cast's own type annotations, then look through to the operand.
                expr.castType?.type?.let { t -> typeStructAnnotation(t) }?.let { return it }
                expr.operand?.let { inferStructAnnotation(it, visited) }
            }
            is PsiParenthesizedExpression -> expr.expression?.let { inferStructAnnotation(it, visited) }
            is PsiConditionalExpression -> expr.thenExpression?.let { inferStructAnnotation(it, visited) }
                ?: expr.elseExpression?.let { inferStructAnnotation(it, visited) }
            else -> null
        }
    }

    private fun typeStructAnnotation(type: PsiType): PsiClass? {
        for (a in type.annotations) {
            val cls = a.nameReferenceElement?.resolve() as? PsiClass ?: continue
            if (cls.isAnnotationType && findRootStruct(cls) != null) return cls
        }
        return null
    }

    private fun emitStructAccessors(
        rootStruct: PsiClass,
        packAnnotation: PsiClass,
        packType: PsiType,
        qualifierText: String,
        result: CompletionResultSet,
    ): Boolean {
        val rootQn = rootStruct.qualifiedName ?: return false
        var emitted = false
        for (fieldClass in rootStruct.innerClasses) {
            if (!fieldClass.isAnnotationType) continue
            val fieldName = fieldClass.name ?: continue

            val scopes = collectAccessorScopes(fieldClass)
            for (scope in scopes) {
                val scopeName = scope.name ?: continue

                // Skip scopes with no pack-annotated method — e.g., `Value` of an enum nested inside a struct.
                if (!scope.methods.any { m ->
                        m.hasModifierProperty(PsiModifier.STATIC) && hasPackParam(m, packAnnotation)
                    }
                ) continue

                for (method in scope.methods) {
                    if (!method.hasModifierProperty(PsiModifier.STATIC)) continue
                    val packIdx = findPackParamIndex(method, packAnnotation, rootStruct, packType)
                    if (packIdx < 0) continue
                    offerAccessor(rootQn, fieldName, scopeName, method, packIdx, packAnnotation, qualifierText, result)
                    emitted = true
                }
            }
        }
        return emitted
    }

    private fun collectAccessorScopes(fieldClass: PsiClass): List<PsiClass> {
        val scopes = mutableListOf<PsiClass>()
        if (fieldClass.methods.any { it.hasModifierProperty(PsiModifier.STATIC) }) scopes.add(fieldClass)
        for (inner in fieldClass.innerClasses) {
            if (inner.isInterface && !inner.isAnnotationType &&
                inner.methods.any { it.hasModifierProperty(PsiModifier.STATIC) }
            ) scopes.add(inner)
        }
        return scopes
    }

    private fun hasPackParam(method: PsiMethod, packAnnotation: PsiClass): Boolean {
        for (p in method.parameterList.parameters) {
            if (paramHasAnnotation(p, packAnnotation)) return true
        }
        return false
    }

    private fun paramHasAnnotation(param: PsiParameter, target: PsiClass): Boolean {
        for (a in param.type.annotations) {
            if ((a.nameReferenceElement?.resolve() as? PsiClass) == target) return true
        }
        for (a in param.annotations) {
            if ((a.nameReferenceElement?.resolve() as? PsiClass) == target) return true
        }
        return false
    }

    private fun findPackParamIndex(
        method: PsiMethod,
        packAnnotation: PsiClass,
        rootStruct: PsiClass,
        packType: PsiType,
    ): Int {
        val params = method.parameterList.parameters
        params.forEachIndexed { i, p -> if (paramHasAnnotation(p, packAnnotation)) return i }

        // Relaxed fallback for reader methods: the generator sometimes emits `get(long src)` without
        // annotating src as `@<Struct>`. We only relax when the return type is NOT `@<Struct>`-annotated
        // (writers always return the struct-annotated type, so offering them with pack in a value slot
        // would be semantic nonsense).
        if (returnTypeAnnotatedWith(method, packAnnotation)) return -1

        params.forEachIndexed { i, p ->
            if (classifyParam(p, packAnnotation, rootStruct) == ParamMatch.UNRELATED &&
                primitiveAssignable(p.type, packType)
            ) return i
        }
        return -1
    }

    private fun returnTypeAnnotatedWith(method: PsiMethod, target: PsiClass): Boolean {
        val rt = method.returnType ?: return false
        for (a in rt.annotations) {
            if ((a.nameReferenceElement?.resolve() as? PsiClass) == target) return true
        }
        return false
    }

    private enum class ParamMatch { PACK, OTHER_STRUCT, UNRELATED }

    private fun classifyParam(param: PsiParameter, packAnnotation: PsiClass, rootStruct: PsiClass): ParamMatch {
        var result = ParamMatch.UNRELATED
        val annotations = param.type.annotations.toList() + param.annotations.toList()
        for (a in annotations) {
            val cls = a.nameReferenceElement?.resolve() as? PsiClass ?: continue
            if (cls == packAnnotation) return ParamMatch.PACK
            if (isWithinStructTree(cls, rootStruct)) result = ParamMatch.OTHER_STRUCT
        }
        return result
    }

    private fun isWithinStructTree(cls: PsiClass, rootStruct: PsiClass): Boolean {
        var c: PsiClass? = cls
        while (c != null) {
            if (c == rootStruct) return true
            c = c.containingClass
        }
        return false
    }

    private fun primitiveAssignable(paramType: PsiType, packType: PsiType): Boolean {
        if (paramType !is PsiPrimitiveType || packType !is PsiPrimitiveType) return false
        return paramType.isAssignableFrom(packType)
    }

    private fun offerAccessor(
        rootQn: String,
        fieldName: String,
        scopeName: String,
        method: PsiMethod,
        packParamIdx: Int,
        packAnnotation: PsiClass,
        qualifierText: String,
        result: CompletionResultSet,
    ) {
        val methodName = method.name
        val params = method.parameterList.parameters

        val args = params.mapIndexed { i, _ -> if (i == packParamIdx) qualifierText else "" }
        val firstEmpty = args.indexOfFirst { it.isEmpty() }

        val sig = params.joinToString(", ", "(", ")") { p ->
            "${p.type.presentableText} ${p.name}"
        }

        val lookup = LookupElementBuilder.create(method, methodName)
            .withIcon(method.getIcon(0))
            .withTypeText(fieldName)
            .withTailText(sig, true)
            .withInsertHandler { ctx, _ ->
                insertStructCall(ctx, method, packAnnotation, rootQn, fieldName, scopeName, methodName, args, firstEmpty)
            }
        result.addElement(lookup)
    }

    private fun insertStructCall(
        ctx: InsertionContext,
        method: PsiMethod,
        packAnnotation: PsiClass,
        rootQn: String,
        fieldName: String,
        scopeName: String,
        methodName: String,
        args: List<String>,
        caretArgIdx: Int,
    ) {
        ctx.commitDocument()
        val file = ctx.file as? PsiJavaFile ?: return

        val ref = PsiTreeUtil.findElementOfClassAtOffset(
            file, ctx.startOffset, PsiReferenceExpression::class.java, false,
        ) ?: return
        val qualifier = ref.qualifierExpression ?: return
        val qualifierText = qualifier.text

        val callPrefix = "$rootQn.$fieldName.$scopeName.$methodName("
        val call = callPrefix + args.joinToString(", ") + ")"

        // When completing a writer (returns @<Struct> <prim>) at a standalone statement
        // position with an assignable qualifier — rewrite the whole statement as
        // `qualifier = call;` so the returned pack is assigned back to the variable.
        val isWriter = returnTypeAnnotatedWith(method, packAnnotation)
        val assignable = qualifier is PsiReferenceExpression && qualifier.resolve() is PsiVariable
        val stmt = run {
            var p: PsiElement? = ref.parent
            while (p is PsiParenthesizedExpression) p = p.parent
            p as? PsiExpressionStatement
        }

        val replaceStart: Int
        val replaceEnd: Int
        val replacement: String
        val prefixLen: Int
        if (isWriter && assignable && stmt != null) {
            val assignPrefix = "$qualifierText = "
            replaceStart = stmt.textRange.startOffset
            replaceEnd = stmt.textRange.endOffset
            replacement = "$assignPrefix$call;"
            prefixLen = assignPrefix.length + callPrefix.length
        } else {
            replaceStart = qualifier.textRange.startOffset
            replaceEnd = ref.textRange.endOffset
            replacement = call
            prefixLen = callPrefix.length
        }

        ctx.document.replaceString(replaceStart, replaceEnd, replacement)
        ctx.commitDocument()

        val caretOffset = if (caretArgIdx >= 0) {
            var off = replaceStart + prefixLen
            for (i in 0 until caretArgIdx) off += args[i].length + 2
            off
        } else {
            replaceStart + replacement.length
        }
        ctx.editor.caretModel.moveToOffset(caretOffset)

        val anchor = file.findElementAt(replaceStart)
        val target: PsiElement = PsiTreeUtil.getParentOfType(anchor, PsiStatement::class.java, false)
            ?: PsiTreeUtil.getParentOfType(anchor, PsiExpression::class.java, false)
            ?: file
        JavaCodeStyleManager.getInstance(ctx.project).shortenClassReferences(target)
    }

    // ================================================================
    // SlimEnum: value-context completion (existing)
    // ================================================================

    private data class EnumCtx(
        val owner: PsiModifierListOwner,
        val expectedType: PsiType,
        val excludes: List<PsiElement>,
    )

    private fun findEnumContext(position: PsiElement): EnumCtx? {
        val polyadicExcludes = mutableListOf<PsiElement>()
        var current: PsiElement? = position.parent
        while (current != null) {
            when (current) {
                is PsiParenthesizedExpression,
                is PsiTypeCastExpression -> Unit

                is PsiPolyadicExpression -> {
                    val op = current.operationTokenType
                    val isComparison = current is PsiBinaryExpression && (
                        op == JavaTokenType.EQEQ || op == JavaTokenType.NE ||
                        op == JavaTokenType.LT || op == JavaTokenType.GT ||
                        op == JavaTokenType.LE || op == JavaTokenType.GE
                    )
                    if (isComparison) {
                        val bin = current
                        val other = when {
                            PsiTreeUtil.isAncestor(bin.rOperand, position, false) -> bin.lOperand
                            PsiTreeUtil.isAncestor(bin.lOperand, position, false) -> bin.rOperand
                            else -> null
                        }
                        val resolved = resolveOwnerAndType(other) ?: return null
                        return EnumCtx(resolved.first, resolved.second, polyadicExcludes)
                    }
                    if (op == JavaTokenType.OR || op == JavaTokenType.AND ||
                        op == JavaTokenType.XOR || op == JavaTokenType.PLUS
                    ) {
                        for (operand in current.operands) {
                            if (!PsiTreeUtil.isAncestor(operand, position, false)) {
                                (operand as? PsiReferenceExpression)?.resolve()
                                    ?.let { polyadicExcludes.add(it) }
                            }
                        }
                    }
                }

                is PsiAssignmentExpression -> {
                    if (!PsiTreeUtil.isAncestor(current.rExpression, position, false)) return null
                    val resolved = resolveOwnerAndType(current.lExpression) ?: return null
                    return EnumCtx(resolved.first, resolved.second, polyadicExcludes)
                }

                is PsiArrayInitializerExpression -> {
                    val arrayType = when (val p = current.parent) {
                        is PsiVariable -> p.type as? PsiArrayType
                        is PsiNewExpression -> p.type as? PsiArrayType
                        else -> null
                    } ?: return null
                    val owner = current.parent as? PsiModifierListOwner ?: return null
                    return EnumCtx(owner, arrayType.componentType, polyadicExcludes)
                }

                is PsiVariable -> {
                    val init = current.initializer
                    if (init != null && PsiTreeUtil.isAncestor(init, position, false)) {
                        return EnumCtx(current, current.type, polyadicExcludes)
                    }
                    return null
                }

                is PsiExpressionList -> {
                    val call = current.parent as? PsiCallExpression ?: return null
                    val method = call.resolveMethodGenerics().element as? PsiMethod ?: return null
                    val args = current.expressions
                    val params = method.parameterList.parameters
                    for ((i, arg) in args.withIndex()) {
                        if (!PsiTreeUtil.isAncestor(arg, position, false)) continue
                        val param = when {
                            i < params.size -> params[i]
                            params.isNotEmpty() && params.last().isVarArgs -> params.last()
                            else -> return null
                        }
                        val pt = param.type
                        val expected = if (param.isVarArgs && pt is PsiArrayType) pt.componentType else pt
                        return EnumCtx(param, expected, polyadicExcludes)
                    }
                    return null
                }

                is PsiReturnStatement -> {
                    val method = PsiTreeUtil.getParentOfType(current, PsiMethod::class.java) ?: return null
                    val rt = method.returnType ?: return null
                    return EnumCtx(method, rt, polyadicExcludes)
                }

                is PsiSwitchLabelStatementBase -> {
                    val switchBlock = PsiTreeUtil.getParentOfType(current, PsiSwitchBlock::class.java) ?: return null
                    val scrutinee = switchBlock.expression ?: return null
                    val resolved = resolveOwnerAndType(scrutinee) ?: return null
                    val caseExcludes = mutableListOf<PsiElement>()
                    switchBlock.body?.statements?.forEach { stmt ->
                        if (stmt is PsiSwitchLabelStatementBase) {
                            stmt.caseLabelElementList?.elements?.forEach { ce ->
                                (ce as? PsiReferenceExpression)?.resolve()?.let { caseExcludes.add(it) }
                            }
                        }
                    }
                    return EnumCtx(resolved.first, resolved.second, caseExcludes)
                }

                is PsiStatement, is PsiMember, is PsiCodeBlock -> return null
            }
            current = current.parent
        }
        return null
    }

    private fun resolveOwnerAndType(expr: PsiExpression?): Pair<PsiModifierListOwner, PsiType>? {
        return when (expr) {
            is PsiReferenceExpression -> when (val r = expr.resolve()) {
                is PsiVariable -> r to r.type
                is PsiMethod -> r to (r.returnType ?: return null)
                else -> null
            }
            is PsiMethodCallExpression -> {
                val m = expr.resolveMethod() ?: return null
                m to (m.returnType ?: return null)
            }
            is PsiArrayAccessExpression -> {
                val arrVar = (expr.arrayExpression as? PsiReferenceExpression)?.resolve() as? PsiVariable
                    ?: return null
                val arrType = arrVar.type as? PsiArrayType ?: return null
                arrVar to arrType.componentType
            }
            else -> null
        }
    }

    private fun addSlimEnumCompletions(ctx: EnumCtx, resultSet: CompletionResultSet): Boolean {
        var added = false
        for (annotation in allAnnotations(ctx.owner)) {
            val annotationClass = annotation.nameReferenceElement?.resolve() as? PsiClass ?: continue
            if (!annotationClass.isAnnotationType) continue
            if (annotationClass.methods.isNotEmpty()) continue

            val constants = annotationClass.fields.filter { f ->
                f.hasModifierProperty(PsiModifier.STATIC) &&
                    f.hasModifierProperty(PsiModifier.FINAL) &&
                    f.initializer != null &&
                    f.type == ctx.expectedType
            }
            if (constants.size < 2) continue

            for (field in constants) {
                if (ctx.excludes.any { it.isEquivalentTo(field) }) continue

                val name = field.name
                val initText = field.initializer?.text.orEmpty()

                val lookup = LookupElementBuilder.create(field, name)
                    .withIcon(field.getIcon(0))
                    .withTypeText(annotationClass.name)
                    .withTailText(" = $initText", true)
                    .withInsertHandler { insertCtx, item ->
                        (item.psiElement as? PsiField)?.let { insertQualified(insertCtx, it) }
                    }
                resultSet.addElement(lookup)
                added = true
            }
        }
        return added
    }

    private fun insertQualified(context: InsertionContext, field: PsiField) {
        val file = context.file as? PsiJavaFile ?: return
        val containingFqn = field.containingClass?.qualifiedName ?: return

        context.commitDocument()
        val existingRef = PsiTreeUtil.findElementOfClassAtOffset(
            file, context.startOffset, PsiReferenceExpression::class.java, false,
        )
        if (existingRef?.qualifierExpression != null) return

        val replacement = "$containingFqn.${field.name}"
        context.document.replaceString(context.startOffset, context.tailOffset, replacement)
        context.commitDocument()

        val newRef = PsiTreeUtil.findElementOfClassAtOffset(
            file, context.startOffset, PsiReferenceExpression::class.java, false,
        )
        val styleManager = JavaCodeStyleManager.getInstance(context.project)
        if (newRef != null) styleManager.shortenClassReferences(newRef) else styleManager.shortenClassReferences(file)
    }
}
