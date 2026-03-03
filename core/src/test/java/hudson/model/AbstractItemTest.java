package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.security.ACL;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.ProjectNamingStrategy;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

/**
 * @author kingfai
 */
class AbstractItemTest {

    private static class StubAbstractItem extends AbstractItem {

        protected StubAbstractItem() {
            // sending in null as parent as I don't care for my current tests
            super(null, "StubAbstractItem");
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Collection<? extends Job> getAllJobs() {
            return null;
        }

        /**
         * Override save so that nothing happens when setDisplayName() is called
         */
        @Override
        public void save() {

        }
    }

    @Test
    void testSetDisplayName() throws Exception {
        final String displayName = "testDisplayName";
        StubAbstractItem i = new StubAbstractItem();
        i.setDisplayName(displayName);
        assertEquals(displayName, i.getDisplayName());
    }

    @Test
    void testGetDefaultDisplayName() {
        final String name = "the item name";
        StubAbstractItem i = new StubAbstractItem();
        i.doSetName(name);
        // assert that if the displayname is not set, the name is actually returned
        assertEquals(name,  i.getDisplayName());

    }

    @Test
    void testSearchNameIsName() {
        final String name = "the item name jlrtlekjtekrjkjr";
        StubAbstractItem i = new StubAbstractItem();
        i.doSetName(name);

        assertEquals(i.getName(),  i.getSearchName());
    }

    @Test
    void testGetDisplayNameOrNull() throws Exception {
        final String projectName = "projectName";
        final String displayName = "displayName";
        StubAbstractItem i = new StubAbstractItem();
        i.doSetName(projectName);
        assertEquals(projectName, i.getName());
        assertNull(i.getDisplayNameOrNull());

        i.setDisplayName(displayName);
        assertEquals(displayName, i.getDisplayNameOrNull());
    }

    @Test
    void testSetDisplayNameOrNull() throws Exception {
        final String projectName = "projectName";
        final String displayName = "displayName";
        StubAbstractItem i = new StubAbstractItem();
        i.doSetName(projectName);
        assertNull(i.getDisplayNameOrNull());

        i.setDisplayNameOrNull(displayName);
        assertEquals(displayName, i.getDisplayNameOrNull());
        assertEquals(displayName, i.getDisplayName());
    }

    /**
     * Subclass-based stub that overrides save() to track invocation count
     * instead of performing real persistence. This lets us verify that
     * methods like setDisplayName() correctly trigger a save without
     * needing a running Jenkins instance or filesystem.
     */
    private static class SaveCountingItem extends AbstractItem {

        private int saveCount = 0;

        protected SaveCountingItem() {
            super(null, "SaveCountingItem");
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Collection<? extends Job> getAllJobs() {
            return null;
        }

        /**
         * Stub: instead of persisting to disk, just count how many times
         * save() was called so tests can verify save behavior.
         */
        @Override
        public void save() {
            saveCount++;
        }

        int getSaveCount() {
            return saveCount;
        }
    }

    @Test
    void testSetDisplayNameCallsSave() throws Exception {
        SaveCountingItem item = new SaveCountingItem();
        assertEquals(0, item.getSaveCount(), "save() should not have been called yet");

        item.setDisplayName("NewDisplay");

        assertEquals(1, item.getSaveCount(), "setDisplayName should trigger exactly one save");
        assertEquals("NewDisplay", item.getDisplayName());
    }

    @Test
    void testMultipleSetDisplayNameCallsSaveEachTime() throws Exception {
        SaveCountingItem item = new SaveCountingItem();

        item.setDisplayName("First");
        item.setDisplayName("Second");
        item.setDisplayName("Third");

        assertEquals(3, item.getSaveCount(), "Each setDisplayName call should trigger one save");
        assertEquals("Third", item.getDisplayName());
    }

    @Test
    void testSetDisplayNameOrNullCallsSave() throws Exception {
        SaveCountingItem item = new SaveCountingItem();

        item.setDisplayNameOrNull("ViaOrNull");

        assertEquals(1, item.getSaveCount(), "setDisplayNameOrNull should trigger save");
        assertEquals("ViaOrNull", item.getDisplayNameOrNull());
    }

    @Test
    void testSaveNotCalledWhenOnlySettingName() {
        SaveCountingItem item = new SaveCountingItem();

        item.doSetName("just-a-name");

        assertEquals(0, item.getSaveCount(), "doSetName should not trigger save");
        assertEquals("just-a-name", item.getName());
    }

    /**
     * Subclass-based stub that overrides both save() and the new
     * notifyItemUpdated() hook. This lets us verify that setDescription()
     * correctly calls save first, then notifies listeners — something that
     * was previously impossible to unit test because notifyItemUpdated()'s
     * logic was an inlined static call to ItemListener.fireOnUpdated().
     */
    private static class DescriptionTrackingItem extends AbstractItem {

        private int saveCount = 0;
        private int notifyCount = 0;
        private final List<String> callOrder = new ArrayList<>();

        protected DescriptionTrackingItem() {
            super(null, "DescriptionTrackingItem");
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Collection<? extends Job> getAllJobs() {
            return null;
        }

        @Override
        public void save() {
            saveCount++;
            callOrder.add("save");
        }

        @Override
        protected void notifyItemUpdated() {
            notifyCount++;
            callOrder.add("notify");
        }

        int getSaveCount() {
            return saveCount;
        }

        int getNotifyCount() {
            return notifyCount;
        }

        List<String> getCallOrder() {
            return callOrder;
        }
    }

    @Test
    void testSetDescriptionSavesAndNotifies() throws Exception {
        DescriptionTrackingItem item = new DescriptionTrackingItem();

        item.setDescription("A new description");

        assertEquals("A new description", item.getDescription());
        assertEquals(1, item.getSaveCount(), "setDescription should trigger exactly one save");
        assertEquals(1, item.getNotifyCount(), "setDescription should trigger exactly one notification");
    }

    @Test
    void testSetDescriptionSavesBeforeNotifying() throws Exception {
        DescriptionTrackingItem item = new DescriptionTrackingItem();

        item.setDescription("test ordering");

        List<String> order = item.getCallOrder();
        assertEquals(2, order.size(), "Exactly two calls should have been recorded");
        assertEquals("save", order.get(0), "save() must be called before notifyItemUpdated()");
        assertEquals("notify", order.get(1), "notifyItemUpdated() must be called after save()");
    }

    @Test
    void testSetDescriptionMultipleTimesTracksEachCall() throws Exception {
        DescriptionTrackingItem item = new DescriptionTrackingItem();

        item.setDescription("first");
        item.setDescription("second");

        assertEquals("second", item.getDescription());
        assertEquals(2, item.getSaveCount());
        assertEquals(2, item.getNotifyCount());
    }

    @Test
    void testSetDescriptionNullClearsDescription() throws Exception {
        DescriptionTrackingItem item = new DescriptionTrackingItem();

        item.setDescription("something");
        item.setDescription(null);

        assertNull(item.getDescription());
        assertEquals(2, item.getSaveCount());
        assertEquals(2, item.getNotifyCount());
    }

    @Test
    void testNotifyNotCalledBySetDisplayName() throws Exception {
        DescriptionTrackingItem item = new DescriptionTrackingItem();

        item.setDisplayName("some display name");

        assertEquals(1, item.getSaveCount(), "setDisplayName should still call save");
        assertEquals(0, item.getNotifyCount(), "setDisplayName should NOT call notifyItemUpdated");
    }

    private static class NameNotEditableItem extends AbstractItem {

        protected NameNotEditableItem(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        public Collection<? extends Job> getAllJobs() {
            return null;
        }

        @Override
        public boolean isNameEditable() {
            return false; //so far it's the default value, but it's good to be explicit for test.
        }
    }

    @Test
    @Issue("JENKINS-58571")
    void renameMethodShouldThrowExceptionWhenNotIsNameEditable() {

        //GIVEN
        NameNotEditableItem item = new NameNotEditableItem(null, "NameNotEditableItem");

        //WHEN
        final IOException e = assertThrows(IOException.class, () -> item.renameTo("NewName"), "An item with isNameEditable false must throw exception when trying to rename it.");

        assertEquals("Trying to rename an item that does not support this operation.", e.getMessage());
        assertEquals("NameNotEditableItem", item.getName());
    }

    @Test
    @Issue("JENKINS-58571")
    void doConfirmRenameMustThrowFormFailureWhenNotIsNameEditable() {

        //GIVEN
        NameNotEditableItem item = new NameNotEditableItem(null, "NameNotEditableItem");

        //WHEN
        final Failure f = assertThrows(Failure.class, () -> item.doConfirmRename("MyNewName"), "An item with isNameEditable false must throw exception when trying to call doConfirmRename.");
        assertEquals("Trying to rename an item that does not support this operation.", f.getMessage());
        assertEquals("NameNotEditableItem", item.getName());
    }

    /**
     * Stub that overrides getACL() to return a controllable ACL, and
     * isNameEditable() to return true, enabling unit tests of the
     * permission-checking and duplicate-name-detection branches of
     * doCheckNewName() — which were previously untestable without a
     * full Jenkins instance.
     */
    private static class RenameCheckableItem extends AbstractItem {

        private final ACL acl;

        protected RenameCheckableItem(ItemGroup parent, String name, ACL acl) {
            super(parent, name);
            this.acl = acl;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Collection<? extends Job> getAllJobs() {
            return null;
        }

        @Override
        public void save() {
        }

        @Override
        public boolean isNameEditable() {
            return true;
        }

        @Override
        public ACL getACL() {
            return acl;
        }
    }

    /**
     * Helper: sets up a MockedStatic for Jenkins.get() so that
     * doCheckNewName() can call Jenkins.get().getProjectNamingStrategy()
     * without a running Jenkins instance.
     */
    private MockedStatic<Jenkins> mockJenkinsWithDefaultNamingStrategy() {
        Jenkins mockJenkins = Mockito.mock(Jenkins.class);
        Mockito.when(mockJenkins.getProjectNamingStrategy())
                .thenReturn(ProjectNamingStrategy.DEFAULT_NAMING_STRATEGY);

        MockedStatic<Jenkins> staticMock = Mockito.mockStatic(Jenkins.class, Mockito.CALLS_REAL_METHODS);
        staticMock.when(Jenkins::get).thenReturn(mockJenkins);
        return staticMock;
    }

    @Test
    void doCheckNewNameReturnsOkWhenUserHasConfigurePermission() {
        // GIVEN: mock parent with no existing item of that name
        @SuppressWarnings("unchecked")
        ItemGroup<Item> mockParent = Mockito.mock(ItemGroup.class);
        Mockito.when(mockParent.getFullName()).thenReturn("");
        Mockito.when(mockParent.getItem("NewValidName")).thenReturn(null);

        // ACL that grants all permissions
        ACL allowAll = ACL.lambda2((auth, perm) -> true);
        RenameCheckableItem item = new RenameCheckableItem(mockParent, "OldName", allowAll);

        try (MockedStatic<Jenkins> jenkinsMock = mockJenkinsWithDefaultNamingStrategy()) {
            // WHEN
            FormValidation result = item.doCheckNewName("NewValidName");

            // THEN
            assertEquals(FormValidation.Kind.OK, result.kind,
                    "Rename should be allowed when user has CONFIGURE permission and name is free");
            // Verify the mock was queried for duplicates (called twice:
            // once as current user, once as SYSTEM to detect hidden items)
            Mockito.verify(mockParent, Mockito.times(2)).getItem("NewValidName");
        }
    }

    @Test
    void doCheckNewNameReturnsErrorWhenNameAlreadyExists() {
        // GIVEN: mock parent that reports the name is already taken
        @SuppressWarnings("unchecked")
        ItemGroup<Item> mockParent = Mockito.mock(ItemGroup.class);
        Mockito.when(mockParent.getFullName()).thenReturn("");
        Item existingItem = Mockito.mock(Item.class);
        Mockito.when(mockParent.getItem("TakenName")).thenReturn(existingItem);

        ACL allowAll = ACL.lambda2((auth, perm) -> true);
        RenameCheckableItem item = new RenameCheckableItem(mockParent, "OldName", allowAll);

        try (MockedStatic<Jenkins> jenkinsMock = mockJenkinsWithDefaultNamingStrategy()) {
            // WHEN
            FormValidation result = item.doCheckNewName("TakenName");

            // THEN
            assertEquals(FormValidation.Kind.ERROR, result.kind,
                    "Rename should be rejected when the name is already in use");
            // Verify the parent was queried — this is behavior checking that
            // only mocking can provide. A stub alone could not confirm that
            // checkIfNameIsUsed actually consulted the parent.
            Mockito.verify(mockParent).getItem("TakenName");
        }
    }

    @Test
    void doCheckNewNameReturnsWarningWhenNameUnchanged() {
        // GIVEN: item whose current name matches the proposed new name
        @SuppressWarnings("unchecked")
        ItemGroup<Item> mockParent = Mockito.mock(ItemGroup.class);
        Mockito.when(mockParent.getFullName()).thenReturn("");

        ACL allowAll = ACL.lambda2((auth, perm) -> true);
        RenameCheckableItem item = new RenameCheckableItem(mockParent, "SameName", allowAll);

        try (MockedStatic<Jenkins> jenkinsMock = mockJenkinsWithDefaultNamingStrategy()) {
            // WHEN
            FormValidation result = item.doCheckNewName("SameName");

            // THEN
            assertEquals(FormValidation.Kind.WARNING, result.kind,
                    "Renaming to the same name should produce a warning, not an error");
            // Verify the parent was never queried — no need to check for duplicates
            // when the name hasn't changed. This negative interaction check is
            // something only mocking can verify.
            Mockito.verify(mockParent, Mockito.never()).getItem(Mockito.anyString());
        }
    }

    @Test
    void doCheckNewNameDeniesRenameWhenUserLacksPermissions() {
        // GIVEN: ACL that denies all permissions
        @SuppressWarnings("unchecked")
        ItemGroup<Item> mockParent = Mockito.mock(ItemGroup.class);
        Mockito.when(mockParent.getFullName()).thenReturn("");

        ACL denyAll = ACL.lambda2((auth, perm) -> false);
        RenameCheckableItem item = new RenameCheckableItem(mockParent, "OldName", denyAll);

        try (MockedStatic<Jenkins> jenkinsMock = mockJenkinsWithDefaultNamingStrategy()) {
            // WHEN: user has no CONFIGURE permission, and the fallback
            // checkPermission(DELETE) should throw AccessDeniedException
            assertThrows(AccessDeniedException.class,
                    () -> item.doCheckNewName("NewName"),
                    "Should deny rename when user lacks both CONFIGURE and DELETE permissions");

            // Verify the parent was never queried for duplicate names —
            // permission check should fail before reaching name validation
            Mockito.verify(mockParent, Mockito.never()).getItem(Mockito.anyString());
        }
    }

}
