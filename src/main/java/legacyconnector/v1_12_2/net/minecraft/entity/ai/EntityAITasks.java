package legacyconnector.v1_12_2.net.minecraft.entity.ai;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The 1.12.2 AI scheduler. Every third tick each task is re-evaluated (start when allowed and wanted, stop when it may no longer
 * continue); in between, running tasks are only checked for continuation. A task may run when every running task of equal or higher
 * priority uses disjoint mutex bits, and no running lower-priority task it would displace is uninterruptible.
 *
 * <p>The task lists of a vanilla mob's view are bound to the modern goal selectors instead: tasks a mod adds become modern goals
 * ({@link org.kylepitcock.legacyconnector.v1_12_2.entity.LegacyGoal1122}) next to the mob's own.</p>
 */
public class EntityAITasks {
    public final Set<EntityAITaskEntry> taskEntries = new LinkedHashSet<>();
    private final Set<EntityAITaskEntry> executingTaskEntries = new LinkedHashSet<>();
    private int tickCount;
    private int tickRate = 3;
    private int disabledControlFlags;
    private net.minecraft.world.entity.Mob mcc$goalOwner;
    private boolean mcc$targets;

    /** Reused on each game thread instead of allocating two collection snapshots for every hosted mob tick. */
    private static final ThreadLocal<TaskSnapshot> TASK_SNAPSHOTS = ThreadLocal.withInitial(TaskSnapshot::new);

    private static TaskSnapshot takeSnapshotBuffer() {
        TaskSnapshot snapshot = TASK_SNAPSHOTS.get();
        if (snapshot.inUse) return new TaskSnapshot(); // keep re-entrant callbacks isolated
        snapshot.inUse = true;
        return snapshot;
    }

    private static void releaseSnapshotBuffer(TaskSnapshot snapshot) {
        snapshot.clear();
        if (snapshot == TASK_SNAPSHOTS.get()) snapshot.inUse = false;
    }

    private static final class TaskSnapshot {
        private static final int MAX_RETAINED_CAPACITY = 1024;
        private EntityAITaskEntry[] taskEntries = new EntityAITaskEntry[8];
        private EntityAITaskEntry[] executingEntries = new EntityAITaskEntry[8];
        private int taskCount;
        private int executingCount;
        private boolean inUse;
        private final Consumer<EntityAITaskEntry> addTask = this::addTask;
        private final Consumer<EntityAITaskEntry> addExecuting = this::addExecuting;

        private void addTask(EntityAITaskEntry entry) {
            if (taskCount == taskEntries.length) taskEntries = Arrays.copyOf(taskEntries, taskEntries.length * 2);
            taskEntries[taskCount++] = entry;
        }

        private void addExecuting(EntityAITaskEntry entry) {
            if (executingCount == executingEntries.length) executingEntries = Arrays.copyOf(executingEntries, executingEntries.length * 2);
            executingEntries[executingCount++] = entry;
        }

        private void captureTasks(Set<EntityAITaskEntry> source) {
            taskCount = 0;
            source.forEach(addTask);
        }

        private void captureExecuting(Set<EntityAITaskEntry> source) {
            executingCount = 0;
            source.forEach(addExecuting);
        }

        private void clear() {
            Arrays.fill(taskEntries, 0, taskCount, null);
            Arrays.fill(executingEntries, 0, executingCount, null);
            taskCount = 0;
            executingCount = 0;
            if (taskEntries.length > MAX_RETAINED_CAPACITY) taskEntries = new EntityAITaskEntry[8];
            if (executingEntries.length > MAX_RETAINED_CAPACITY) executingEntries = new EntityAITaskEntry[8];
        }
    }

    public EntityAITasks() {
    }

    /** {@code new EntityAITasks(world.profiler)}: the profiler is not used. */
    public EntityAITasks(Object profiler) {
    }

    /** {@code new EntityAITasks(world.profiler)}: the legacy profiler records nothing for the AI (the modern one profiles the goals). */
    public EntityAITasks(legacyconnector.v1_12_2.net.minecraft.profiler.Profiler profiler) {
    }

    /** Binds the list to a vanilla mob's goal selector (target selector for target tasks). */
    public final void mcc$bindGoals(legacyconnector.v1_12_2.net.minecraft.entity.EntityLiving owner, boolean targets) {
        if (owner.mcc$entity instanceof net.minecraft.world.entity.Mob m) {
            this.mcc$goalOwner = m;
            this.mcc$targets = targets;
        }
    }

    private net.minecraft.world.entity.ai.goal.GoalSelector mcc$selector() {
        var m = mcc$goalOwner;
        if (m == null) return null;
        return mcc$targets ? m.targetSelector : m.goalSelector;
    }

    public void addTask(int priority, EntityAIBase task) {
        if (task == null) return;
        EntityAITaskEntry entry = new EntityAITaskEntry(priority, task);
        taskEntries.add(entry);
        var selector = mcc$selector();
        if (selector != null) {
            entry.mcc$goal = new org.kylepitcock.legacyconnector.v1_12_2.entity.LegacyGoal1122(task, mcc$targets);
            selector.addGoal(priority, entry.mcc$goal);
        }
    }

    public void removeTask(EntityAIBase task) {
        Iterator<EntityAITaskEntry> it = taskEntries.iterator();
        while (it.hasNext()) {
            EntityAITaskEntry e = it.next();
            if (e.action != task) continue;
            if (e.using) {
                e.using = false;
                e.action.resetTask();
                executingTaskEntries.remove(e);
            }
            var selector = mcc$selector();
            if (selector != null && e.mcc$goal != null) selector.removeGoal(e.mcc$goal);
            it.remove();
            return;
        }
    }

    public void onUpdateTasks() {
        if (mcc$selector() != null) return; // the modern goal selector runs them
        TaskSnapshot snapshot = takeSnapshotBuffer();
        try {
            if (tickCount++ % tickRate == 0) {
                snapshot.captureTasks(taskEntries);
                for (int i = 0; i < snapshot.taskCount; i++) {
                    EntityAITaskEntry e = snapshot.taskEntries[i];
                    if (e.using) {
                        if (!canUse(e) || !canContinue(e)) {
                            e.using = false;
                            safeReset(e.action, AiCallback.RESET_DURING_REEVALUATION);
                            executingTaskEntries.remove(e);
                        }
                    } else if (canUse(e) && safeShouldExecute(e.action)) {
                        e.using = true;
                        safeStart(e.action);
                        executingTaskEntries.add(e);
                    }
                }
            } else {
                Iterator<EntityAITaskEntry> it = executingTaskEntries.iterator();
                while (it.hasNext()) {
                    EntityAITaskEntry e = it.next();
                    if (!canContinue(e)) {
                        e.using = false;
                        safeReset(e.action, AiCallback.RESET_DURING_CONTINUATION_CHECK);
                        it.remove();
                    }
                }
            }
            if (!executingTaskEntries.isEmpty()) {
                snapshot.captureExecuting(executingTaskEntries);
                for (int i = 0; i < snapshot.executingCount; i++) safeUpdate(snapshot.executingEntries[i].action);
            }
        } finally {
            releaseSnapshotBuffer(snapshot);
        }
    }

    private enum AiCallback {
        SHOULD_EXECUTE, SHOULD_CONTINUE, RESET_DURING_REEVALUATION, RESET_DURING_CONTINUATION_CHECK, START, UPDATE
    }

    private static final Set<AiCallback> FAILED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** An AI task that throws (an API the layer lacks) is reported once per callback site and treated as not running. */
    private static void failed(AiCallback callback, Throwable e) {
        if (FAILED.add(callback)) org.slf4j.LoggerFactory.getLogger("LegacyConnector/Entities").warn("An AI task failed: {}", e.toString());
    }

    private static boolean safeShouldExecute(EntityAIBase task) {
        try {
            return task.shouldExecute();
        } catch (RuntimeException | LinkageError e) {
            failed(AiCallback.SHOULD_EXECUTE, e);
            return false;
        }
    }

    private static boolean safeShouldContinue(EntityAIBase task) {
        try {
            return task.shouldContinueExecuting();
        } catch (RuntimeException | LinkageError e) {
            failed(AiCallback.SHOULD_CONTINUE, e);
            return false;
        }
    }

    private static void safeReset(EntityAIBase task, AiCallback callback) {
        try {
            task.resetTask();
        } catch (RuntimeException | LinkageError e) {
            failed(callback, e);
        }
    }

    private static void safeStart(EntityAIBase task) {
        try {
            task.startExecuting();
        } catch (RuntimeException | LinkageError e) {
            failed(AiCallback.START, e);
        }
    }

    private static void safeUpdate(EntityAIBase task) {
        try {
            task.updateTask();
        } catch (RuntimeException | LinkageError e) {
            failed(AiCallback.UPDATE, e);
        }
    }

    private boolean canContinue(EntityAITaskEntry e) {
        return safeShouldContinue(e.action);
    }

    private boolean canUse(EntityAITaskEntry e) {
        if ((e.action.getMutexBits() & disabledControlFlags) != 0) return false;
        for (EntityAITaskEntry other : executingTaskEntries) {
            if (other == e) continue;
            if (e.priority >= other.priority) {
                if (!areTasksCompatible(e, other)) return false;
            } else if (!other.action.isInterruptible()) {
                return false;
            }
        }
        return true;
    }

    private boolean areTasksCompatible(EntityAITaskEntry a, EntityAITaskEntry b) {
        return (a.action.getMutexBits() & b.action.getMutexBits()) == 0;
    }

    public boolean isControlFlagDisabled(int flag) {
        return (disabledControlFlags & flag) > 0;
    }

    public void disableControlFlag(int flag) {
        disabledControlFlags |= flag;
    }

    public void enableControlFlag(int flag) {
        disabledControlFlags &= ~flag;
    }

    public void setControlFlag(int flag, boolean enabled) {
        if (enabled) enableControlFlag(flag);
        else disableControlFlag(flag);
    }

    public class EntityAITaskEntry {
        public final EntityAIBase action;
        public final int priority;
        public boolean using;
        org.kylepitcock.legacyconnector.v1_12_2.entity.LegacyGoal1122 mcc$goal;

        public EntityAITaskEntry(int priority, EntityAIBase action) {
            this.priority = priority;
            this.action = action;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof EntityAITaskEntry e && action.equals(e.action);
        }

        @Override
        public int hashCode() {
            return action.hashCode();
        }
    }
}
