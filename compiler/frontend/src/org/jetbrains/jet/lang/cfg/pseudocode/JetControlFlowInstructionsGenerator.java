package org.jetbrains.jet.lang.cfg.pseudocode;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.cfg.*;
import org.jetbrains.jet.lang.psi.*;

import java.util.*;

/**
 * @author abreslav
 */
public class JetControlFlowInstructionsGenerator extends JetControlFlowBuilderAdapter {

    private final Stack<BreakableBlockInfo> loopInfo = new Stack<BreakableBlockInfo>();
    private final Map<JetElement, BreakableBlockInfo> elementToBlockInfo = new HashMap<JetElement, BreakableBlockInfo>();
    private int labelCount = 0;

    private final Stack<JetControlFlowInstructionsGeneratorWorker> builders = new Stack<JetControlFlowInstructionsGeneratorWorker>();

    private final Stack<BlockInfo> allBlocks = new Stack<BlockInfo>();

    private final JetPseudocodeTrace trace;

    public JetControlFlowInstructionsGenerator(JetPseudocodeTrace trace) {
        this.trace = trace;
    }

    private void pushBuilder(JetElement scopingElement, JetElement subroutine) {
        JetControlFlowInstructionsGeneratorWorker worker = new JetControlFlowInstructionsGeneratorWorker(scopingElement, subroutine);
        builders.push(worker);
        builder = worker;
    }

    private JetControlFlowInstructionsGeneratorWorker popBuilder(@NotNull JetElement element) {
        JetControlFlowInstructionsGeneratorWorker worker = builders.pop();
        trace.recordControlFlowData(element, worker.getPseudocode());
        if (!builders.isEmpty()) {
            builder = builders.peek();
        }
        else {
            builder = null;
        }
        return worker;
    }

    @Override
    public void enterSubroutine(@NotNull JetDeclaration subroutine) {
        pushBuilder(subroutine, subroutine);
        assert builder != null;
        builder.enterSubroutine(subroutine);
    }

    @Override
    public void exitSubroutine(@NotNull JetDeclaration subroutine) {
        super.exitSubroutine(subroutine);
        JetControlFlowInstructionsGeneratorWorker worker = popBuilder(subroutine);
        if (!builders.empty()) {
            JetControlFlowInstructionsGeneratorWorker builder = builders.peek();
            LocalDeclarationInstruction instruction = new LocalDeclarationInstruction(subroutine, worker.getPseudocode());
            builder.add(instruction);
        }
    }

    private class JetControlFlowInstructionsGeneratorWorker implements JetControlFlowBuilder {

        private final Pseudocode pseudocode;
        private final Label error;
        private final Label sink;
        private final JetElement currentSubroutine;

        private JetControlFlowInstructionsGeneratorWorker(@NotNull JetElement scopingElement, @NotNull JetElement currentSubroutine) {
            this.pseudocode = new Pseudocode(scopingElement);
            this.error = pseudocode.createLabel("error");
            this.sink = pseudocode.createLabel("sink");
            this.currentSubroutine = currentSubroutine;
        }

        public Pseudocode getPseudocode() {
            return pseudocode;
        }

        private void add(@NotNull Instruction instruction) {
            pseudocode.addInstruction(instruction);
            if (instruction instanceof JetElementInstruction) {
                JetElementInstruction elementInstruction = (JetElementInstruction) instruction;
                trace.recordRepresentativeInstruction(elementInstruction.getElement(), instruction);
            }
        }

        @NotNull
        @Override
        public final Label createUnboundLabel() {
            return pseudocode.createLabel("l" + labelCount++);
        }

        @Override
        public final LoopInfo enterLoop(@NotNull JetExpression expression, Label loopExitPoint, Label conditionEntryPoint) {
            Label label = createUnboundLabel();
            bindLabel(label);
            LoopInfo blockInfo = new LoopInfo(
                    expression,
                    label,
                    loopExitPoint != null ? loopExitPoint : createUnboundLabel(),
                    createUnboundLabel(),
                    conditionEntryPoint != null ? conditionEntryPoint : createUnboundLabel());
            loopInfo.push(blockInfo);
            elementToBlockInfo.put(expression, blockInfo);
            allBlocks.push(blockInfo);
            trace.recordLoopInfo(expression, blockInfo);
            return blockInfo;
        }

        @Override
        public final void exitLoop(@NotNull JetExpression expression) {
            BreakableBlockInfo info = loopInfo.pop();
            elementToBlockInfo.remove(expression);
            allBlocks.pop();
            bindLabel(info.getExitPoint());
        }

        @Override
        public JetElement getCurrentLoop() {
            return loopInfo.empty() ? null : loopInfo.peek().getElement();
        }

        @Override
        public void enterSubroutine(@NotNull JetDeclaration subroutine) {
            Label entryPoint = createUnboundLabel();
            BreakableBlockInfo blockInfo = new BreakableBlockInfo(subroutine, entryPoint, createUnboundLabel());
//            subroutineInfo.push(blockInfo);
            elementToBlockInfo.put(subroutine, blockInfo);
            allBlocks.push(blockInfo);
            bindLabel(entryPoint);
            add(new SubroutineEnterInstruction(subroutine));
        }

        @Override
        public JetElement getCurrentSubroutine() {
            return currentSubroutine;// subroutineInfo.empty() ? null : subroutineInfo.peek().getElement();
        }

        @Override
        public Label getEntryPoint(@NotNull JetElement labelElement) {
            return elementToBlockInfo.get(labelElement).getEntryPoint();
        }

        @Override
        public Label getExitPoint(@NotNull JetElement labelElement) {
            BreakableBlockInfo blockInfo = elementToBlockInfo.get(labelElement);
            assert blockInfo != null : labelElement.getText();
            return blockInfo.getExitPoint();
        }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        private void handleJumpInsideTryFinally(Label jumpTarget) {
            List<TryFinallyBlockInfo> finallyBlocks = new ArrayList<TryFinallyBlockInfo>();

            for (int i = allBlocks.size() - 1; i >= 0; i--) {
                BlockInfo blockInfo = allBlocks.get(i);
                if (blockInfo instanceof BreakableBlockInfo) {
                    BreakableBlockInfo breakableBlockInfo = (BreakableBlockInfo) blockInfo;
                    if (jumpTarget == breakableBlockInfo.getExitPoint() || jumpTarget == breakableBlockInfo.getEntryPoint()) {
                        for (int j = finallyBlocks.size() - 1; j >= 0; j--) {
                            finallyBlocks.get(j).generateFinallyBlock();
                        }
                        break;
                    }
                }
                else if (blockInfo instanceof TryFinallyBlockInfo) {
                    TryFinallyBlockInfo tryFinallyBlockInfo = (TryFinallyBlockInfo) blockInfo;
                    finallyBlocks.add(tryFinallyBlockInfo);
                }
            }
        }

        @Override
        public void exitSubroutine(@NotNull JetDeclaration subroutine) {
            bindLabel(getExitPoint(subroutine));
            pseudocode.addExitInstruction(new SubroutineExitInstruction(subroutine, "<END>"));
            bindLabel(error);
            add(new SubroutineExitInstruction(subroutine, "<ERROR>"));
            bindLabel(sink);
            pseudocode.addSinkInstruction(new SubroutineSinkInstruction(subroutine, "<SINK>"));
            elementToBlockInfo.remove(subroutine);
            allBlocks.pop();
        }

        @Override
        public void returnValue(@NotNull JetExpression returnExpression, @NotNull JetElement subroutine) {
            Label exitPoint = getExitPoint(subroutine);
            handleJumpInsideTryFinally(exitPoint);
            add(new ReturnValueInstruction(returnExpression, exitPoint));
        }

        @Override
        public void returnNoValue(@NotNull JetElement returnExpression, @NotNull JetElement subroutine) {
            Label exitPoint = getExitPoint(subroutine);
            handleJumpInsideTryFinally(exitPoint);
            add(new ReturnNoValueInstruction(returnExpression, exitPoint));
        }

        @Override
        public void write(@NotNull JetElement assignment, @NotNull JetElement lValue) {
            add(new WriteValueInstruction(assignment, lValue));
        }

        @Override
        public void read(@NotNull JetExpression expression) {
            add(new ReadValueInstruction(expression));
        }

        @Override
        public void readUnit(@NotNull JetExpression expression) {
            add(new ReadUnitValueInstruction(expression));
        }

        @Override
        public void jump(@NotNull Label label) {
            handleJumpInsideTryFinally(label);
            add(new UnconditionalJumpInstruction(label));
        }

        @Override
        public void jumpOnFalse(@NotNull Label label) {
            handleJumpInsideTryFinally(label);
            add(new ConditionalJumpInstruction(false, label));
        }

        @Override
        public void jumpOnTrue(@NotNull Label label) {
            handleJumpInsideTryFinally(label);
            add(new ConditionalJumpInstruction(true, label));
        }

        @Override
        public void bindLabel(@NotNull Label label) {
            pseudocode.bindLabel(label);
        }

        @Override
        public void allowDead() {
            Label allowedDeadLabel = createUnboundLabel();
            bindLabel(allowedDeadLabel);
            pseudocode.allowDead(allowedDeadLabel);
        }

        @Override
        public void nondeterministicJump(Label label) {
            handleJumpInsideTryFinally(label);
            add(new NondeterministicJumpInstruction(label));
        }

        @Override
        public void nondeterministicJump(List<Label> labels) {
            //todo
            //handleJumpInsideTryFinally(label);
            add(new NondeterministicJumpInstruction(labels));
        }

        @Override
        public void jumpToError(JetThrowExpression expression) {
            add(new UnconditionalJumpInstruction(error));
        }
        
        @Override
        public void jumpToError(JetExpression nothingExpression) {
            add(new UnconditionalJumpInstruction(error));
        }

        @Override
        public void enterTryFinally(@NotNull GenerationTrigger generationTrigger) {
            allBlocks.push(new TryFinallyBlockInfo(generationTrigger));
        }

        @Override
        public void exitTryFinally() {
            BlockInfo pop = allBlocks.pop();
            assert pop instanceof TryFinallyBlockInfo;
        }

        @Override
        public void unsupported(JetElement element) {
            add(new UnsupportedElementInstruction(element));
        }
    }

    public static class TryFinallyBlockInfo extends BlockInfo {
        private final GenerationTrigger finallyBlock;

        private TryFinallyBlockInfo(GenerationTrigger finallyBlock) {
            this.finallyBlock = finallyBlock;
        }

        public void generateFinallyBlock() {
            finallyBlock.generate();
        }
    }

}
