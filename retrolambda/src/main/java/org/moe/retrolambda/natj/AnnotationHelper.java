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
import java.util.ArrayList;
import java.util.List;

public class AnnotationHelper {

	public static final boolean DEBUG = false;

	private final Annotation[] parentAnnotations;
	private final boolean hasOptionals;
	private final ArrayList<Class<?>> annotations = new ArrayList<>();

	public AnnotationHelper(Annotation[] parent, boolean hasOptionals) {
		this.parentAnnotations = parent;
		this.hasOptionals = hasOptionals;
	}

	public void add(String ann) {
		if (ann == null) {
			throw new NullPointerException();
		}
		add(NatJRuntime.getClassFor(ann.substring(1, ann.length() - 1)));
	}

	public void add(Class<?> ann) {
		if (ann == null) {
			throw new NullPointerException();
		}
		annotations.add(ann);
	}

	public boolean hasSelectorAnnotation() {
		return annotations.contains(NatJRuntime.Annotations.SELECTOR);
	}

	private ArrayList<Class<?>> getNewCompleteList() {
		return new ArrayList<>(hasOptionals ? NatJRuntime.Annotations.OPTIONALS
				: NatJRuntime.Annotations.NON_OPTIONALS);
	}

	private ArrayList<Class<?>> getNewRetainList() {
		ArrayList<Class<?>> list = new ArrayList<>();
		for (Annotation annotation : parentAnnotations) {
			list.add(annotation.annotationType());
		}
		return list;
	}

	public ArrayList<Class<?>> getInjectList() {
		ArrayList<Class<?>> list = getNewCompleteList();
		list.retainAll(getNewRetainList());
		list.removeAll(annotations);
		return list;
	}

	public void validate(String desc, int index) {
		final ArrayList<Class<?>> parentAnns = new ArrayList<Class<?>>();
		for (Annotation ann : parentAnnotations) {
			parentAnns.add(ann.getClass());
		}
		final ArrayList<Class<?>> definedAnns = new ArrayList<>(annotations);
		final ArrayList<Class<?>> allAnns = new ArrayList<>(definedAnns);
		allAnns.removeAll(parentAnns);
		allAnns.addAll(parentAnns);

		boolean isReturn = index == -1;
		debugPrint("VA--> " + desc);
		validateAnnotationTypes(desc, allAnns, isReturn);
		validateAnnotationCollision(desc, allAnns, isReturn);
	}

	private void validateAnnotationTypes(String desc,
			ArrayList<Class<?>> allAnns, boolean isReturn) {
		List<Class<?>> enabledAnns = isReturn ? NatJRuntime.Annotations.RETURN_ANNS
				: NatJRuntime.Annotations.PARAM_ANNS;
		for (Class<?> cls : allAnns) {
			if (!enabledAnns.contains(cls)
					&& NatJRuntime.Annotations.ALL_ANNS.contains(cls)) {
				throw new RuntimeException("Annotation " + cls.getName()
						+ " is not allowed on "
						+ (isReturn ? "return type/method" : "parameter")
						+ "! Method: " + desc);
			}
		}
	}

	private void validateAnnotationCollision(String desc,
			ArrayList<Class<?>> allAnns, boolean b) {
		for (List<Class<?>> anns : NatJRuntime.Annotations.COLLIDING_ANNS) {
			int count = 0;
			for (Class<?> ann : allAnns) {
				debugPrint("VAC: " + ann.getName(), 1);
				count += (anns.contains(ann) ? 1 : 0);
			}
			debugPrint("VAC Count: " + count, 1);
			if (count > 1) {
				ArrayList<Class<?>> tmp = new ArrayList<>(allAnns);
				tmp.retainAll(anns);
				throw new RuntimeException("Annotations " + tmp
						+ " can't be specified at the same time! Method: "
						+ desc);
			}
		}
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
