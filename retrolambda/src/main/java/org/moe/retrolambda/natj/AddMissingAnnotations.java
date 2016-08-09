/*
 * Copyright 2014-2016 Intel Corporation
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

package org.moe.retrolambda.natj;

import static org.objectweb.asm.Opcodes.ASM5;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class AddMissingAnnotations extends ClassVisitor {

	public static final boolean DEBUG = false;

	private boolean skip = false;
	private String[] interfaces;
	private String superName;

	private String className;

	public AddMissingAnnotations(ClassVisitor next) {
		super(ASM5, next);
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		this.className = name;
		this.superName = superName;
		this.interfaces = interfaces;
		this.skip = name.startsWith("org/moe/natj/");
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public MethodVisitor visitMethod(int access, final String methodName,
			String desc, String signature, String[] exceptions) {
		MethodVisitor visitor = super.visitMethod(access, methodName, desc,
				signature, exceptions);
		if (skip) {
			return visitor;
		}
		Method _parent = null;
		try {
			_parent = NatJRuntime.getParentImplementation(superName,
					interfaces, methodName, desc);
		} catch (Throwable ex) {
			System.out.println("Warning: failed to locate parent method implementation for " +  className +
					"." + methodName + desc);
		}
		final Method parent = _parent;
		if (parent == null) {
			return visitor;
		}

		debugPrint("Updating method from '" + parent.toGenericString() + "'");

		// Create annotation helpers for method and parameters
		final AnnotationHelper methodHelper = new AnnotationHelper(
				parent.getDeclaredAnnotations(), true);
		final ArrayList<AnnotationHelper> paramHelpers = new ArrayList<>();
		for (Annotation[] annotations : parent.getParameterAnnotations()) {
			paramHelpers.add(new AnnotationHelper(annotations, true));
		}

		return new MethodVisitor(ASM5, visitor) {
			boolean isVisiting = true;

			@Override
			public AnnotationVisitor visitAnnotation(String desc,
					boolean visible) {
				if (isVisiting) {
					methodHelper.add(desc);
				}
				return super.visitAnnotation(desc, visible);
			}

			@Override
			public AnnotationVisitor visitParameterAnnotation(int parameter,
					String desc, boolean visible) {
				if (isVisiting) {
					paramHelpers.get(parameter).add(desc);
				}
				return super.visitParameterAnnotation(parameter, desc, visible);
			}

			@Override
			public void visitCode() {
				injectAnnotations();
				super.visitCode();
			}

			@Override
			public void visitEnd() {
				injectAnnotations();
				super.visitEnd();
			}

			private void injectAnnotations() {
				if (isVisiting) {
					isVisiting = false;
					if (!methodHelper.hasSelectorAnnotation()) {
						_injectAnnotations();
						System.out.println("Injected NatJ annotations into "
								+ className + "." + methodName);
					}
				}
			}

			private void _injectAnnotations() {
				methodHelper.validate(className + "." + methodName, -1);
				for (Class<?> cls : methodHelper.getInjectList()) {
					injectAnnotation(-1, parent, cls);
				}
				if (paramHelpers.size() != parent.getParameterCount()) {
					throw new IllegalStateException();
				}
				int i = 0;
				for (AnnotationHelper paramHelper : paramHelpers) {
					paramHelper.validate(className + "." + methodName, i);
					for (Class<?> cls : paramHelper.getInjectList()) {
						injectAnnotation(i, parent.getParameters()[i], cls);
					}
					++i;
				}
			}

			@SuppressWarnings("unchecked")
			private void injectAnnotation(int index, AnnotatedElement parent,
					Class<?> cls) {
				Annotation annotation = parent
						.getAnnotation((Class<Annotation>) cls);
				Method[] fields = cls.getDeclaredMethods();
				if (annotation != null) {
					String annClsName = annotation.annotationType().getName();
					debugPrint("Injecting " + annClsName, 1);
					String annName = annClsName.replaceAll("\\.", "/");

					AnnotationVisitor av;
					if (index == -1) {
						av = visitAnnotation("L" + annName + ";", true);
					} else {
						av = visitParameterAnnotation(index, "L" + annName
								+ ";", true);
					}
					for (Method field : fields) {
						try {
							field.setAccessible(true);
							String fldName = field.getName();
							Object fldValue = field.invoke(annotation);
							fldValue = convertClassToType(fldValue);
							if (fldValue.getClass().isArray()) {
								Object[] arr = (Object[]) fldValue;
								AnnotationVisitor av2 = av.visitArray(fldName);
								for (Object obj : arr) {
									av2.visit(null, obj);
								}
								av2.visitEnd();
							} else {
								av.visit(fldName, fldValue);
							}
						} catch (IllegalAccessException e) {
							throw new RuntimeException(e);
						} catch (IllegalArgumentException e) {
							continue;
						} catch (InvocationTargetException e) {
							throw new RuntimeException(e);
						}
					}
				}
			}

			private Object convertClassToType(Object value) {
				if (value instanceof Class) {
					value = Type.getType((Class<?>) value);
				}
				if (!value.getClass().isArray()) {
					return value;
				}
				Object[] values = (Object[]) value;
				if (values.getClass().getComponentType() == Class.class) {
					Type types[] = new Type[values.length];
					for (int i = 0; i < values.length; ++i) {
						types[i] = (Type) convertClassToType(values[i]);
					}
					return types;
				} else {
					for (int i = 0; i < values.length; ++i) {
						values[i] = convertClassToType(values[i]);
					}
					return values;
				}
			}
		};
	}

	private static void debugPrint(String value, int depth) {
		debugPrint(value, "", depth);
	}

	private static void debugPrint(String value, String prefix, int depth) {
		if (!DEBUG)
			return;
		String format = "%1$" + depth * 2 + "s" + prefix + value;
		System.out.println(String.format(format, ""));
	}

	private static void debugPrint(String value) {
		if (!DEBUG)
			return;
		System.out.println(value);
	}
}
