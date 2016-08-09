/*
 * Copyright (C) 2014-2016 Intel Corporation
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

import java.util.ListIterator;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class AddMissingNatJRegister extends ClassVisitor {

	public static final boolean DEBUG = false;

	// No information
	private static final int NATJREG_UNKNOWN = -1;

	// Has clinit, contains NatJ.register() and it is the first instruction
	private static final int NATJREG_LEAVE_ALONE = 2;

	// Has clinit but doesn't contain NatJ.register()
	private static final int NATJREG_INSERT_FIRST = 3;

	private boolean skip = false;
	private boolean visit = false;
	private int action = NATJREG_UNKNOWN;
	private MethodNode CLI;

	private String name;

	public AddMissingNatJRegister(ClassVisitor next) {
		super(ASM5, next);
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		this.name = name;
		skip = name.startsWith("org/moe/natj/");
		if (!skip) {
			visit = NatJRuntime.isNativeObjectDescendant(superName);
		}
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		if (!skip && !visit) {
			visit = NatJRuntime.RUNTIME_ANNOTATION_DESC.equals(desc);
		}
		return super.visitAnnotation(desc, visible);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {
		if (skip || !visit)
			return super.visitMethod(access, name, desc, signature, exceptions);
		if (CLI != null)
			return super.visitMethod(access, name, desc, signature, exceptions);

		if ("<clinit>".equals(name)) {
			CLI = new MethodNode(access, name, desc, signature, exceptions);
			return CLI;
		}

		return super.visitMethod(access, name, desc, signature, exceptions);
	}

	@Override
	public void visitEnd() {
		if (!visit) {
			super.visitEnd();
			return;
		}

		if (CLI != null) {
			action = NATJREG_INSERT_FIRST;
			InsnList insts = CLI.instructions;
			ListIterator<AbstractInsnNode> it = insts.iterator();
			int idx = 0;
			while (it.hasNext()) {
				AbstractInsnNode n = it.next();
				if (n instanceof MethodInsnNode) {
					MethodInsnNode i = (MethodInsnNode) n;
					if (NatJRuntime.isNatJRegisterInsn(i.owner, i.name, i.desc)) {
						action = NATJREG_LEAVE_ALONE;
						debugPrint("Found NatJ.register() among instructions");
						break;
					}
				}
				++idx;
			}
			if (idx == insts.size()) {
				action = NATJREG_INSERT_FIRST;
				debugPrint("Didn't find NatJ.register() among instructions");
			}

			MethodVisitor mv = super.visitMethod(CLI.access, CLI.name,
					CLI.desc, CLI.signature,
					CLI.exceptions.toArray(new String[] {}));
			if (action == NATJREG_INSERT_FIRST) {
				mv.visitCode();
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
						NatJRuntime.NATJ_OWNER, NatJRuntime.NATJ_REGISTER_NAME,
						NatJRuntime.NATJ_REGISTER_DESC, false);
				CLI.accept(mv);
				System.out.println("Injected NatJ.register() into " + name);
			} else {
				mv.visitCode();
				CLI.accept(mv);
			}
		} else {
			MethodVisitor mv = super.visitMethod(Opcodes.ACC_STATIC,
					"<clinit>", "()V", null, null);
			mv.visitCode();
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, NatJRuntime.NATJ_OWNER,
					NatJRuntime.NATJ_REGISTER_NAME,
					NatJRuntime.NATJ_REGISTER_DESC, false);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(-1, -1);
			mv.visitEnd();
			System.out.println("Injected NatJ.register() into " + name);
		}

		super.visitEnd();
	}

	private static void debugPrint(String value) {
		if (!DEBUG)
			return;
		System.out.println(value);
	}
}
