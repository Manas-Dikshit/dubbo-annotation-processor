/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dubbo.annotation.handler;

import org.apache.dubbo.annotation.AnnotationProcessingHandler;
import org.apache.dubbo.annotation.AnnotationProcessorContext;
import org.apache.dubbo.annotation.util.ASTUtils;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;

import javax.lang.model.element.Element;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Handles @Deprecated annotation and adds a logger warning and method invocation tracking 
 * for deprecated methods and constructors.
 */
public class DeprecatedHandler implements AnnotationProcessingHandler {

    @Override
    public Set<Class<? extends Annotation>> getAnnotationsToHandle() {
        return new HashSet<>(Collections.singletonList(Deprecated.class));
    }

    @Override
    public void process(Set<Element> elements, AnnotationProcessorContext apContext) {
        for (Element element : elements) {
            // Only process methods and constructors
            if (!(element instanceof Symbol.MethodSymbol methodSymbol)) {
                continue;
            }

            Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) element.getEnclosingElement();

            // Check if it's a constructor
            boolean isConstructor = methodSymbol.name.toString().equals(classSymbol.name.toString());

            // Import necessary classes
            ASTUtils.addImportStatement(apContext, classSymbol, "org.apache.dubbo.common", "DeprecatedMethodInvocationCounter");
            ASTUtils.addImportStatement(apContext, classSymbol, "org.slf4j", "Logger");
            ASTUtils.addImportStatement(apContext, classSymbol, "org.slf4j", "LoggerFactory");

            JCTree methodTree = apContext.getJavacTrees().getTree(element);
            apContext.getMessager().printMessage(javax.tools.Diagnostic.Kind.WARNING,
                    "Usage of deprecated " + (isConstructor ? "constructor" : "method") + " detected: " + getMethodDefinition(classSymbol, methodSymbol));

            methodTree.accept(new TreeTranslator() {
                @Override
                public void visitMethodDef(JCTree.JCMethodDecl jcMethodDecl) {
                    JCTree.JCBlock block = jcMethodDecl.body;
                    if (block == null) {
                        // No method body (i.e., interface method declaration)
                        return;
                    }

                    // Insert logging and counter tracking
                    ASTUtils.insertStatementToHeadOfMethod(block, jcMethodDecl, generateLoggerStatement(apContext, classSymbol));
                    ASTUtils.insertStatementToHeadOfMethod(block, jcMethodDecl, generateCounterStatement(apContext, classSymbol, jcMethodDecl));
                }
            });
        }
    }

    /**
     * Generate a statement to track deprecated method invocations.
     * Example: DeprecatedMethodInvocationCounter.onDeprecatedMethodCalled("class.method(params)");
     */
    private JCTree.JCExpressionStatement generateCounterStatement(AnnotationProcessorContext apContext,
                                                                  Symbol.ClassSymbol classSymbol,
                                                                  JCTree.JCMethodDecl originalMethodDecl) {
        JCTree.JCExpression fullStatement = apContext.getTreeMaker().Apply(
            com.sun.tools.javac.util.List.nil(),
            apContext.getTreeMaker().Select(
                apContext.getTreeMaker().Ident(apContext.getNames().fromString("DeprecatedMethodInvocationCounter")),
                apContext.getNames().fromString("onDeprecatedMethodCalled")
            ),
            com.sun.tools.javac.util.List.of(
                apContext.getTreeMaker().Literal(getMethodDefinition(classSymbol, originalMethodDecl))
            )
        );

        return apContext.getTreeMaker().Exec(fullStatement);
    }

    /**
     * Generate a logger statement that logs a warning when a deprecated method is called.
     * Example: logger.warn("Deprecated method called: class.method(params)\n Stack trace: ...");
     */
    private JCTree.JCExpressionStatement generateLoggerStatement(AnnotationProcessorContext apContext, Symbol.ClassSymbol classSymbol) {
        // Generate logger initialization statement
        JCTree.JCExpression loggerInit = apContext.getTreeMaker().Apply(
            com.sun.tools.javac.util.List.nil(),
            apContext.getTreeMaker().Select(
                apContext.getTreeMaker().Ident(apContext.getNames().fromString("LoggerFactory")),
                apContext.getNames().fromString("getLogger")
            ),
            com.sun.tools.javac.util.List.of(apContext.getTreeMaker().Literal(classSymbol.getQualifiedName().toString()))
        );

        // logger.warn("Deprecated method called: ...", new Exception());
        JCTree.JCExpression logStatement = apContext.getTreeMaker().Apply(
            com.sun.tools.javac.util.List.nil(),
            apContext.getTreeMaker().Select(
                apContext.getTreeMaker().Ident(apContext.getNames().fromString("logger")),
                apContext.getNames().fromString("warn")
            ),
            com.sun.tools.javac.util.List.of(
                apContext.getTreeMaker().Literal("Deprecated method called in " + classSymbol.getQualifiedName()),
                apContext.getTreeMaker().NewClass(
                    null, com.sun.tools.javac.util.List.nil(),
                    apContext.getTreeMaker().Ident(apContext.getNames().fromString("Exception")),
                    com.sun.tools.javac.util.List.nil(),
                    null
                )
            )
        );

        return apContext.getTreeMaker().Exec(logStatement);
    }

    private String getMethodDefinition(Symbol.ClassSymbol classSymbol, Symbol.MethodSymbol methodSymbol) {
        return classSymbol.getQualifiedName() + "." + methodSymbol.name.toString() + "(" + methodSymbol.params.toString() + ")";
    }
}
