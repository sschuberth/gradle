/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.properties.annotations;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.DeclaredTaskOutputFileProperty;
import org.gradle.api.internal.tasks.PropertySpecFactory;
import org.gradle.api.internal.tasks.ValidatingValue;
import org.gradle.api.internal.tasks.properties.BeanPropertyContext;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.internal.reflect.PropertyMetadata;

import static org.gradle.api.internal.tasks.properties.annotations.InputPropertyAnnotationHandlerUtils.isOptional;

public abstract class AbstractOutputPropertyAnnotationHandler implements PropertyAnnotationHandler {

    protected abstract DeclaredTaskOutputFileProperty createFileSpec(ValidatingValue value, PropertySpecFactory specFactory);

    @Override
    public boolean shouldVisit(PropertyVisitor visitor) {
        return true;
    }

    @Override
    public void visitPropertyValue(String propertyName, ValidatingValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor, PropertySpecFactory specFactory, FileResolver fileResolver, BeanPropertyContext context) {
        DeclaredTaskOutputFileProperty fileSpec = createFileSpec(value, specFactory);
        fileSpec
            .withPropertyName(propertyName)
            .optional(isOptional(propertyMetadata));
        visitor.visitOutputFileProperty(fileSpec);
    }
}
