/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.annotation;

import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.ProcessingEnvironment;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Context Object of Annotation Processor, which stores objects related to javac.
 */
public class AnnotationProcessorContext {
    private static final Logger LOGGER = Logger.getLogger(AnnotationProcessorContext.class.getName());
    
    private JavacProcessingEnvironment javacProcessingEnvironment;
    private JavacTrees javacTrees;
    private TreeMaker treeMaker;
    private Names names;
    private Context javacContext;
    private Trees trees;

    private AnnotationProcessorContext() {
        // Private constructor to enforce usage through factory method
    }

    private static <T> T jbUnwrap(Class<? extends T> iface, T wrapper) {
        T unwrapped = null;
        try {
            final Class<?> apiWrappers = wrapper.getClass().getClassLoader().loadClass("org.jetbrains.jps.javac.APIWrappers");
            final Method unwrapMethod = apiWrappers.getDeclaredMethod("unwrap", Class.class, Object.class);
            unwrapped = iface.cast(unwrapMethod.invoke(null, iface, wrapper));
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Failed to unwrap ProcessingEnvironment: " + e.getMessage(), e);
        }

        return unwrapped != null ? unwrapped : wrapper;
    }

    public static AnnotationProcessorContext fromProcessingEnvironment(ProcessingEnvironment processingEnv) {
        AnnotationProcessorContext apContext = new AnnotationProcessorContext();
        
        if (processingEnv == null) {
            throw new IllegalArgumentException("ProcessingEnvironment cannot be null");
        }

        Object procEnvToUnwrap = processingEnv.getClass() == JavacProcessingEnvironment.class ?
            processingEnv : jbUnwrap(JavacProcessingEnvironment.class, processingEnv);

        if (!(procEnvToUnwrap instanceof JavacProcessingEnvironment)) {
            throw new IllegalStateException("Failed to obtain JavacProcessingEnvironment");
        }

        JavacProcessingEnvironment jcProcessingEnvironment = (JavacProcessingEnvironment) procEnvToUnwrap;
        Context context = jcProcessingEnvironment.getContext();

        apContext.javacProcessingEnvironment = jcProcessingEnvironment;
        apContext.javacContext = context;
        apContext.javacTrees = JavacTrees.instance(jcProcessingEnvironment);
        apContext.treeMaker = TreeMaker.instance(context);
        apContext.names = Names.instance(context);
        apContext.trees = Trees.instance(jcProcessingEnvironment);

        // Ensure all required components are initialized
        if (apContext.javacTrees == null || apContext.treeMaker == null || apContext.names == null || apContext.trees == null) {
            throw new IllegalStateException("Failed to initialize AnnotationProcessorContext due to missing components.");
        }

        LOGGER.info("Successfully created AnnotationProcessorContext.");
        return apContext;
    }

    public JavacTrees getJavacTrees() {
        return javacTrees;
    }

    public TreeMaker getTreeMaker() {
        return treeMaker;
    }

    public Names getNames() {
        return names;
    }

    public Context getJavacContext() {
        return javacContext;
    }

    public Trees getTrees() {
        return trees;
    }

    public JavacProcessingEnvironment getJavacProcessingEnvironment() {
        return javacProcessingEnvironment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        AnnotationProcessorContext that = (AnnotationProcessorContext) o;
        return Objects.equals(javacProcessingEnvironment, that.javacProcessingEnvironment) &&
               Objects.equals(javacTrees, that.javacTrees) &&
               Objects.equals(treeMaker, that.treeMaker) &&
               Objects.equals(names, that.names) &&
               Objects.equals(javacContext, that.javacContext) &&
               Objects.equals(trees, that.trees);
    }

    @Override
    public int hashCode() {
        return Objects.hash(javacProcessingEnvironment, javacTrees, treeMaker, names, javacContext, trees);
    }

    @Override
    public String toString() {
        return "AnnotationProcessorContext{" +
                "javacProcessingEnvironment=" + javacProcessingEnvironment +
                ", javacTrees=" + javacTrees +
                ", treeMaker=" + treeMaker +
                ", names=" + names +
                ", javacContext=" + javacContext +
                ", trees=" + trees +
                '}';
    }
}
