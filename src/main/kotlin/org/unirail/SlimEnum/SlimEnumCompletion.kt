package org.unirail.SlimEnum

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.VariableLookupItem
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.NotNull

class SlimEnumCompletion : CompletionContributor() {
	
	init {
		extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
			
			override fun addCompletions(@NotNull parameters: CompletionParameters, context: ProcessingContext, @NotNull dst: CompletionResultSet) {
				
				
				val expr_at_edit_pos = parameters.position
				var VAR: PsiVariable? = null
				
				PsiTreeUtil.findFirstParent(expr_at_edit_pos) { e ->
					
					when (e) {
						is PsiSwitchLabelStatement -> {
							
							val switch = PsiTreeUtil.getParentOfType(e, PsiSwitchStatement::class.java) as PsiSwitchStatement
							
							val exclude = mutableListOf<PsiElement>()
							switch.body?.let {
								for (sw_state in it.statements)
									if (sw_state is PsiSwitchLabelStatement) collect(sw_state.caseValues, exclude)
							}
							
							
							
							switch.expression?.let {
								PsiTreeUtil.processElements(it) { ref ->
									if (ref is PsiReferenceExpression)
										when (val orig = ref.resolve()) {
											is PsiVariable -> !fill2_switch(AnnotationUtil.getAllAnnotations(orig, true, null), orig.type, exclude, dst)
											is PsiMethod   -> !fill2_switch(AnnotationUtil.getAllAnnotations(orig, true, null), orig.returnType!!, exclude, dst)
											else           -> true
										} else true
								}
							}
							
							true
						}
						
						is PsiMethodCallExpression -> {
							
							val declared_method = e.methodExpression.resolve() as PsiMethod
							val declared_params = declared_method.parameterList.parameters
							
							if (declared_params.isNotEmpty()) {
								
								val args = e.argumentList
								val editPosArg = PsiTreeUtil.findFirstParent(expr_at_edit_pos) { par -> par.parent == args }
								
								var index = 0
								var arg_expr = editPosArg
								
								var ch = e.argumentList.firstChild
								val last = e.argumentList.lastChild
								var exit = false
								
								while (ch != last) {
									
									when {
										ch.node.elementType.index.toInt() == 680 -> { //COMMA
											if (exit) break
											arg_expr = editPosArg
											index++
										}
										
										ch is PsiExpression && ch.type != null   -> arg_expr = e //typed expression
									}
									
									if (ch == editPosArg) exit = true
									
									ch = ch.nextSibling
								}
								
								
								val param = declared_params[index]
								fill(AnnotationUtil.getAllAnnotations(param, true, null), param.type, collect(arg_expr), dst)
							}
							true
						}
						
						is PsiAssignmentExpression -> {
							(e.lExpression as PsiReferenceExpression).resolve().let {
								if (it is PsiVariable) {
									val annotations = AnnotationUtil.getAllAnnotations(it, true, null)
									
									fill(annotations, it.type, collect(e.rExpression), dst)
								}
							}
							true
						}
						
						is PsiVariable             -> { //helper
							VAR = e
							false
						}
						
						is PsiDeclarationStatement -> {
							
							VAR?.let { fill(AnnotationUtil.getAllAnnotations(it, true, null), it.type, collect(it.initializer), dst) }
							true
						}
						
						is PsiBinaryExpression     -> {
							
							val L = e.lOperand
							
							if (L is PsiReferenceExpression)
								L.resolve()?.let {
									when (it) {
										is PsiVariable -> fill(AnnotationUtil.getAllAnnotations(it, true, null), it.type, collect(e.rOperand), dst)
										is PsiMethod   -> fill(AnnotationUtil.getAllAnnotations(it, true, null), it.returnType, collect(e.rOperand), dst)
										else           -> false
									}
								} ?: false else false
							
						}
						is PsiReturnStatement      -> {
							
							PsiTreeUtil.findFirstParent(e) { m ->
								if (m is PsiMethod) {
									
									fill(AnnotationUtil.getAllAnnotations(m, true, null), m.returnType, collect(e), dst)
									true
								} else false
							}
							
							true
						}
						
						else                       -> false
					}
				}
			}
			
			fun collect(src: PsiElement?) = collect(src, mutableListOf())
			fun collect(src: PsiElement?, dst: MutableList<PsiElement>): MutableList<PsiElement> {
				PsiTreeUtil.processElements(src) { el ->
					if (el is PsiReference)
						el.resolve()?.let { dst += it }
					true
				}
				return dst
			}
			
			fun fill(src: Array<PsiAnnotation>, datatype: PsiType?, excludes: Collection<PsiElement>?, dst: CompletionResultSet): Boolean {
				if (src.isEmpty() || datatype == null) return false
				
				for (ann in src) {
					val annotation = ann.resolveAnnotationType() ?: continue
					
					if (annotation.methods.isNotEmpty()) continue
					
					val fields = annotation.fields.filter { fld -> fld.type == datatype } //type restriction
					if (fields.size < 2) continue
					
					// fields with none-primitive type is always normal enum
					//
					// but primitive type fields
					// if first and second field's names are in alphabetic order:
					//          this is normal enum. a constant value for field can be used only once, values combinations have no sense
					//          otherwise flag! a constant value combination for field is ok
					
					if (excludes != null && excludes.isNotEmpty() &&
							(fields[0].type !is PsiPrimitiveType || fields[0].name.compareTo(fields[1].name) < 0) &&  //normal enum
							fields.any { fld -> excludes.any { e -> e.isEquivalentTo(fld) } } //check only once used
					) return true
					
					for (fld in fields) {
						dst.addElement(object : VariableLookupItem(fld, false) {
							override fun renderElement(presentation: LookupElementPresentation) {
								super.renderElement(presentation)
								presentation.setTailText(" = " + fld.initializer!!.text, true)
							}
						})
					}
					
					return true
				}
				return false
			}
			
			fun fill2_switch(src: Array<PsiAnnotation>, datatype: PsiType?, excludes: Collection<PsiElement>?, dst: CompletionResultSet): Boolean {
				if (src.isEmpty() || datatype == null) return false
				
				for (ann in src) {
					val annotation = ann.resolveAnnotationType() ?: continue
					
					if (annotation.methods.isNotEmpty()) continue
					
					val fields = annotation.fields.filter { fld -> fld.type == datatype } //type restriction
					if (fields.size < 2) continue
					
					
					val chk = excludes != null && excludes.isNotEmpty() &&
							(fields[0].type !is PsiPrimitiveType || fields[0].name.compareTo(fields[1].name) < 0)  //normal enum
					
					
					for (fld in fields) {
						if (chk && excludes!!.any { e -> e.isEquivalentTo(fld) }) continue //check only once used. toss out used
						
						dst.addElement(object : VariableLookupItem(fld, false) {
							override fun renderElement(presentation: LookupElementPresentation) {
								super.renderElement(presentation)
								presentation.setTailText(" = " + fld.initializer!!.text, true)
							}
						})
					}
					
					return true
				}
				return false
			}
		})
	}
	
}