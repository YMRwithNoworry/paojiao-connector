package legacyconnector.v1_12_2.net.minecraft.entity.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EntityAITasks1122Test {
    @Test
    void snapshotsKeepTaskRegistrationChangesOutOfTheCurrentStartPass() {
        EntityAITasks tasks = new EntityAITasks();
        TestTask added = new TestTask();
        TestTask first = new TestTask(() -> tasks.addTask(1, added), null);
        tasks.addTask(0, first);

        tasks.onUpdateTasks();

        assertEquals(1, first.starts);
        assertEquals(0, added.starts);

        tasks.onUpdateTasks();
        tasks.onUpdateTasks();
        tasks.onUpdateTasks();

        assertEquals(1, first.starts);
        assertEquals(1, added.starts);
    }

    @Test
    void reentrantUpdatesUseAnIndependentSnapshot() {
        EntityAITasks tasks = new EntityAITasks();
        boolean[] reentered = {false};
        TestTask first = new TestTask(null, () -> {
            if (!reentered[0]) {
                reentered[0] = true;
                tasks.onUpdateTasks();
            }
        });
        TestTask second = new TestTask();
        tasks.addTask(0, first);
        tasks.addTask(1, second);

        tasks.onUpdateTasks();

        assertEquals(2, first.updates);
        assertEquals(2, second.updates);
    }

    @Test
    void updateSnapshotsKeepTaskRegistrationChangesOutOfTheCurrentUpdatePass() {
        EntityAITasks tasks = new EntityAITasks();
        TestTask added = new TestTask();
        TestTask[] first = new TestTask[1];
        first[0] = new TestTask(null, () -> {
            tasks.removeTask(first[0]);
            tasks.addTask(1, added);
        });
        tasks.addTask(0, first[0]);

        tasks.onUpdateTasks();

        assertEquals(1, first[0].updates);
        assertEquals(0, added.starts);
        assertEquals(0, added.updates);

        tasks.onUpdateTasks();
        tasks.onUpdateTasks();
        tasks.onUpdateTasks();

        assertEquals(1, added.starts);
        assertEquals(1, added.updates);
    }

    private static class TestTask extends EntityAIBase {
        private final Runnable onStart;
        private final Runnable onUpdate;
        private int starts;
        private int updates;

        private TestTask() {
            this(null, null);
        }

        private TestTask(Runnable onStart, Runnable onUpdate) {
            this.onStart = onStart;
            this.onUpdate = onUpdate;
        }

        @Override
        public boolean shouldExecute() {
            return true;
        }

        @Override
        public void startExecuting() {
            starts++;
            if (onStart != null) onStart.run();
        }

        @Override
        public void updateTask() {
            updates++;
            if (onUpdate != null) onUpdate.run();
        }
    }
}
