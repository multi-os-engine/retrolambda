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

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class NatJRuntime {

	public static final class Annotations {
		private Annotations() {
		}

		public static final Class<?> BY_VALUE = getClassFor("org.moe.natj.general.ann.ByValue");
		public static final Class<?> MAPPED = getClassFor("org.moe.natj.general.ann.Mapped");
		public static final Class<?> MAPPED_RETURN = getClassFor("org.moe.natj.general.ann.MappedReturn");
		public static final Class<?> NFLOAT = getClassFor("org.moe.natj.general.ann.NFloat");
		public static final Class<?> NINT = getClassFor("org.moe.natj.general.ann.NInt");
		public static final Class<?> NUINT = getClassFor("org.moe.natj.general.ann.NUInt");
		public static final Class<?> OWNED = getClassFor("org.moe.natj.general.ann.Owned");
		public static final Class<?> REFERENCE_INFO = getClassFor("org.moe.natj.general.ann.ReferenceInfo");

		public static final Class<?> FUNCTION_PTR = getClassFor("org.moe.natj.c.ann.FunctionPtr");

		public static final Class<?> IBACTION = getClassFor("org.moe.natj.objc.ann.IBAction");
		public static final Class<?> IBOUTLET = getClassFor("org.moe.natj.objc.ann.IBOutlet");
		public static final Class<?> IBOUTLET_COLLECTION = getClassFor("org.moe.natj.objc.ann.IBOutletCollection");
		public static final Class<?> NOT_IMPLEMENTED = getClassFor("org.moe.natj.objc.ann.NotImplemented");
		public static final Class<?> OBJC_BLOCK = getClassFor("org.moe.natj.objc.ann.ObjCBlock");
		public static final Class<?> SELECTOR = getClassFor("org.moe.natj.objc.ann.Selector");

		public static final List<Class<?>> OPTIONALS = Collections
				.unmodifiableList(Arrays.asList(new Class<?>[] { BY_VALUE,
						MAPPED, MAPPED_RETURN, NFLOAT, NINT, NUINT, OWNED,
						REFERENCE_INFO, FUNCTION_PTR, IBACTION, IBOUTLET,
						IBOUTLET_COLLECTION, NOT_IMPLEMENTED, OBJC_BLOCK,
						SELECTOR }));

		public static final List<Class<?>> NON_OPTIONALS = Collections
				.unmodifiableList(Arrays.asList(new Class<?>[] { BY_VALUE,
						MAPPED, MAPPED_RETURN, NFLOAT, NINT, NUINT, OWNED,
						REFERENCE_INFO, FUNCTION_PTR, IBACTION, IBOUTLET,
						IBOUTLET_COLLECTION, OBJC_BLOCK, SELECTOR }));

		public static final List<List<Class<?>>> COLLIDING_ANNS;

		static {
			ArrayList<List<Class<?>>> lists = new ArrayList<>();
			lists.add(Collections.unmodifiableList(Arrays
					.asList(new Class<?>[] { NFLOAT, NINT, NUINT })));
			lists.add(Collections.unmodifiableList(Arrays
					.asList(new Class<?>[] { FUNCTION_PTR, OBJC_BLOCK })));
			lists.add(Collections.unmodifiableList(Arrays
					.asList(new Class<?>[] { IBACTION, IBOUTLET,
							IBOUTLET_COLLECTION })));
			lists.add(Collections.unmodifiableList(Arrays
					.asList(new Class<?>[] { MAPPED, MAPPED_RETURN })));
			COLLIDING_ANNS = Collections.unmodifiableList(lists);
		}
		
		public static final List<Class<?>> ALL_ANNS = Collections
				.unmodifiableList(Arrays.asList(new Class<?>[] { BY_VALUE,
						MAPPED, MAPPED_RETURN, NFLOAT, NINT, NUINT, OWNED,
						REFERENCE_INFO, FUNCTION_PTR, IBACTION, IBOUTLET,
						IBOUTLET_COLLECTION, NOT_IMPLEMENTED, OBJC_BLOCK,
						SELECTOR }));
		
		public static final List<Class<?>> RETURN_ANNS = Collections
				.unmodifiableList(Arrays.asList(new Class<?>[] { BY_VALUE,
						MAPPED_RETURN, NFLOAT, NINT, NUINT, OWNED,
						REFERENCE_INFO, FUNCTION_PTR, IBACTION, IBOUTLET,
						IBOUTLET_COLLECTION, NOT_IMPLEMENTED, OBJC_BLOCK,
						SELECTOR }));

		public static final List<Class<?>> PARAM_ANNS = Collections
				.unmodifiableList(Arrays.asList(new Class<?>[] { BY_VALUE,
						MAPPED, NFLOAT, NINT, NUINT, OWNED, REFERENCE_INFO,
						FUNCTION_PTR, OBJC_BLOCK }));
	}

	public static final boolean DEBUG = false;

	public static final String NATJ_NATIVE_OBJECT = "org.moe.natj.general.NativeObject";
	public static final String NATJ_OWNER = "org/moe/natj/general/NatJ";
	public static final String NATJ_REGISTER_DESC = "()V";
	public static final String NATJ_REGISTER_NAME = "register";
	public static final String RUNTIME_ANNOTATION_DESC = "Lorg/moe/natj/general/ann/Runtime;";

	public static boolean isNatJRegisterInsn(String owner, String name,
			String desc) {
		return NATJ_OWNER.equals(owner) && NATJ_REGISTER_NAME.equals(name)
				&& NATJ_REGISTER_DESC.equals(desc);
	}

	public static boolean isNativeObjectDescendant(String superName) {
		try {
			debugPrint("isNativeObjectDescendant: " + superName);
			Class<?> cls = getClassFor(superName);
			int depth = 0;
			do {
				++depth;
				debugPrint(cls.getName(), depth);
				if (NatJRuntime.NATJ_NATIVE_OBJECT.equals(cls.getName())) {
					return true;
				}
			} while ((cls = getSuper(cls)) != null);
		} catch (Throwable ex) {
			System.out.println("Warning: failed to process class hierarchy, assuming class '" +
					superName + "' is not NativeObject descendant");
		}
		return false;
	}

	public static Method getParentImplementation(String superName,
			String[] interfaces, String name, String desc) {
		debugPrint("getRootImplementation: " + superName + ", " + name + ", "
				+ desc);

		Class<?> superCls = null;
		Class<?> cls = getClassFor(superName);
		int depth = 0;
		do {
			++depth;
			debugPrint(cls.getName(), depth);

			Method method = getDeclaredMethod(cls, name, getParamClasses(desc));
			if (hasSelectorAnn(method)) {
				debugPrint("Match " + method, depth + 2);
				return method;
			}

			HashSet<Class<?>> itfs = new HashSet<>();
			if (superCls == null) {
				for (int i = 0; i < interfaces.length; ++i) {
					collectInterfaces(getClassFor(interfaces[i]), itfs);
				}
			} else {
				for (Class<?> itf : superCls.getInterfaces()) {
					collectInterfaces(itf, itfs);
				}
			}
			for (Class<?> itf : itfs) {
				method = getDeclaredMethod(itf, name, getParamClasses(desc));
				if (hasSelectorAnn(method)) {
					debugPrint("Match " + method, depth + 2);
					return method;
				}
			}

		} while ((superCls = cls) != null && (cls = getSuper(cls)) != null);
		return null;
	}

	private static void collectInterfaces(Class<?> classFor,
			HashSet<Class<?>> itfs) {
		itfs.add(classFor);
		for (Class<?> inner : classFor.getInterfaces()) {
			if (!itfs.contains(inner)) {
				collectInterfaces(inner, itfs);
			}
		}
	}

	private static boolean hasSelectorAnn(Method method) {
		if (method == null) {
			return false;
		}
		Annotation[] annotations = method.getDeclaredAnnotations();
		for (Annotation annotation : annotations) {
			if (Annotations.SELECTOR.equals(annotation.annotationType())) {
				return true;
			}
		}
		return false;
	}

	private static Method getDeclaredMethod(Class<?> cls, String name,
			Class<?>... parameterTypes) {
		if (cls == null) {
			return null;
		}
		Method method;
		try {
			method = cls.getDeclaredMethod(name, parameterTypes);
		} catch (NoSuchMethodException e) {
			return null;
		} catch (SecurityException e) {
			return null;
		}
		return method;
	}

	private static ClassLoader getClassLoader() {
		return Thread.currentThread().getContextClassLoader();
	}

	private static Class<?> getSuper(Class<?> cls) {
		return cls.getSuperclass();
	}

	public static Class<?> getClassFor(String cls) {
		try {
			return getClassLoader().loadClass(cls.replaceAll("[\\/]", "."));
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private static Class<?>[] getParamClasses(String desc) {
		if (desc == null) {
			throw new IllegalArgumentException();
		}
		if (!desc.startsWith("(")) {
			throw new IllegalArgumentException();
		}
		ArrayList<Class<?>> classes = new ArrayList<Class<?>>();
		int start = 1;
		int dim = 0;
		while (desc.codePointAt(start) != ')') {
			Class<?> cls = null;
			int codePoint = desc.codePointAt(start);
			switch (codePoint) {
			case 'B':
				cls = byte.class;
				++start;
				break;
			case 'C':
				cls = char.class;
				++start;
				break;
			case 'D':
				cls = double.class;
				++start;
				break;
			case 'F':
				cls = float.class;
				++start;
				break;
			case 'I':
				cls = int.class;
				++start;
				break;
			case 'J':
				cls = long.class;
				++start;
				break;
			case 'S':
				cls = short.class;
				++start;
				break;
			case 'Z':
				cls = boolean.class;
				++start;
				break;
			case '[':
				++dim;
				++start;
				break;
			case 'L': {
				++start;
				int end = desc.indexOf(';', start);
				if (end == -1) {
					throw new IllegalStateException();
				}
				cls = getClassFor(desc.substring(start, end));
				start = end + 1;
			}
				break;

			default:
				throw new UnsupportedOperationException();
			}

			if (cls != null) {
				while (dim > 0) {
					--dim;
					cls = Array.newInstance(cls, 0).getClass();
				}
				classes.add(cls);
			}
		}
		return classes.toArray(new Class<?>[classes.size()]);
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
