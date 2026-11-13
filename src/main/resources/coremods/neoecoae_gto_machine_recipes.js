var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
var IincInsnNode = Java.type('org.objectweb.asm.tree.IincInsnNode');
var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');
var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var ECO = 'cn/dancingsnow/neoecoae/compat/gto/ECOGtoMachineRecipes';

function initializeCoreMod() {
    return {
        'neoecoae_gto_machine_recipes': {
            'target': {
                'type': 'METHOD',
                'class': 'com.gtocore.data.Data',
                'methodName': 'commonInit',
                'methodDesc': '()V'
            },
            'transformer': function(method) {
                var nodes = method.instructions.toArray();
                var anchors = 0;
                var materialInfo = false;
                for (var i = 0; i < nodes.length; i++) {
                    var node = nodes[i];
                    if (node.getOpcode() === Opcodes.GETSTATIC &&
                        node.owner === 'com/gregtechceu/gtceu/api/data/chemical/material/ItemMaterialData' &&
                        node.name === 'ITEM_MATERIAL_INFO') materialInfo = true;
                    if (node.getOpcode() !== Opcodes.INVOKESTATIC ||
                        !materialInfo || node.owner.indexOf('com/gtolib/') !== 0 ||
                        node.desc !== '()V') continue;
                    materialInfo = false;

                    var index = method.maxLocals++;
                    var loop = new LabelNode();
                    var end = new LabelNode();
                    var registration = new InsnList();
                    registration.add(new InsnNode(Opcodes.ICONST_0));
                    registration.add(new VarInsnNode(Opcodes.ISTORE, index));
                    registration.add(loop);
                    registration.add(new VarInsnNode(Opcodes.ILOAD, index));
                    registration.add(ASMAPI.buildMethodCall(ECO, 'count', '()I', ASMAPI.MethodType.STATIC));
                    registration.add(new JumpInsnNode(Opcodes.IF_ICMPGE, end));
                    registration.add(new VarInsnNode(Opcodes.ILOAD, index));
                    registration.add(ASMAPI.buildMethodCall(ECO, 'type',
                        '(I)Lcom/gregtechceu/gtceu/api/recipe/GTRecipeType;', ASMAPI.MethodType.STATIC));
                    registration.add(new InsnNode(Opcodes.DUP));
                    registration.add(ASMAPI.buildMethodCall(ECO, 'builderHandle',
                        '(Lcom/gregtechceu/gtceu/api/recipe/GTRecipeType;)Ljava/lang/invoke/MethodHandle;', ASMAPI.MethodType.STATIC));
                    registration.add(new InsnNode(Opcodes.SWAP));
                    registration.add(new VarInsnNode(Opcodes.ILOAD, index));
                    registration.add(ASMAPI.buildMethodCall(ECO, 'id',
                        '(I)Lnet/minecraft/resources/ResourceLocation;', ASMAPI.MethodType.STATIC));
                    // Native GTO checks the caller of recipeBuilder and save. Keep both calls in Data.
                    registration.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                        'java/lang/invoke/MethodHandle', 'invokeExact',
                        '(Lcom/gregtechceu/gtceu/api/recipe/GTRecipeType;Lnet/minecraft/resources/ResourceLocation;)Lcom/gregtechceu/gtceu/api/recipe/GTRecipeBuilder;', false));
                    registration.add(new InsnNode(Opcodes.DUP));
                    registration.add(new VarInsnNode(Opcodes.ILOAD, index));
                    registration.add(ASMAPI.buildMethodCall(ECO, 'configure',
                        '(Lcom/gregtechceu/gtceu/api/recipe/GTRecipeBuilder;I)V', ASMAPI.MethodType.STATIC));
                    registration.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                        'com/gregtechceu/gtceu/api/recipe/GTRecipeBuilder', 'save',
                        '()Lcom/gregtechceu/gtceu/api/recipe/GTRecipeDefinition;', false));
                    registration.add(new VarInsnNode(Opcodes.ILOAD, index));
                    registration.add(ASMAPI.buildMethodCall(ECO, 'verifySaved',
                        '(Lcom/gregtechceu/gtceu/api/recipe/GTRecipeDefinition;I)V', ASMAPI.MethodType.STATIC));
                    registration.add(new IincInsnNode(index, 1));
                    registration.add(new JumpInsnNode(Opcodes.GOTO, loop));
                    registration.add(end);
                    method.instructions.insertBefore(node, registration);
                    method.instructions.insert(node, ASMAPI.buildMethodCall(ECO, 'verifyFinalized',
                        '()V', ASMAPI.MethodType.STATIC));
                    anchors++;
                }
                if (anchors !== 1) throw new Error('NeoECOAE expected one GTO recipe finalization after material info, found ' + anchors);
                method.maxStack = Math.max(method.maxStack, 4);
                ASMAPI.log('INFO', 'NeoECOAE installed GTO machine recipe registration and finalization checks');
                return method;
            }
        }
    };
}
