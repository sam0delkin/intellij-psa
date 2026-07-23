package com.github.sam0delkin.intellijpsa.ui.components

import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Container
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.Element

class JTextFieldCollectionTest : BasePlatformTestCase() {
    @Suppress("UNCHECKED_CAST")
    private fun <T> findAll(
        root: Container,
        klass: Class<T>,
    ): List<T> {
        val found = ArrayList<T>()
        for (component in root.components) {
            if (klass.isInstance(component)) {
                found.add(component as T)
            }
            if (component is Container) {
                found.addAll(findAll(component, klass))
            }
        }

        return found
    }

    private fun textFields(coll: JTextFieldCollection): List<JTextField> = findAll(coll, JTextField::class.java)

    private fun actionButtons(coll: JTextFieldCollection): List<ActionButton> = findAll(coll, ActionButton::class.java)

    @Suppress("UNCHECKED_CAST")
    private fun internalValues(coll: JTextFieldCollection): List<String> {
        val field = JTextFieldCollection::class.java.getDeclaredField("values")
        field.isAccessible = true

        return ArrayList(field.get(coll) as ArrayList<String>)
    }

    fun testInitialStateHasNoRowsAndOnlyAddButton() {
        val coll = JTextFieldCollection()

        assertEquals(0, textFields(coll).size)
        assertEquals(1, actionButtons(coll).size)
        assertEquals("Add Row", actionButtons(coll)[0].action.templatePresentation.text)
        assertEquals(0, internalValues(coll).size)
    }

    fun testSetValuesRendersOneRowPerValue() {
        val coll = JTextFieldCollection()

        coll.setValues(listOf("a", "b"))

        val fields = textFields(coll)
        assertEquals(2, fields.size)
        assertEquals("a", fields[0].text)
        assertEquals("b", fields[1].text)

        val buttons = actionButtons(coll)
        assertEquals(3, buttons.size)
        assertEquals(2, buttons.count { it.action.templatePresentation.text == "Remove Row" })
        assertEquals(1, buttons.count { it.action.templatePresentation.text == "Add Row" })

        assertEquals(listOf("a", "b"), internalValues(coll))
    }

    fun testSetValuesOverwritesPreviousValuesOnSubsequentCalls() {
        val coll = JTextFieldCollection()
        coll.setValues(listOf("a", "b", "c"))

        coll.setValues(listOf("x"))

        val fields = textFields(coll)
        assertEquals(1, fields.size)
        assertEquals("x", fields[0].text)
        assertEquals(listOf("x"), internalValues(coll))
    }

    /**
     * `setValues` calls `this.values.clear(); this.values.addAll(newValues)` and only THEN calls
     * `pcs.firePropertyChange("values", values, values)` - passing the very same (already mutated)
     * list instance as both the old and new value. `java.beans.PropertyChangeSupport` suppresses
     * delivery whenever `oldValue != null && newValue != null && oldValue.equals(newValue)`, which
     * is unconditionally true here (it is the same list). As a result, listeners registered via
     * `addValuesChangeListener` are never actually notified by `setValues()` - see final report.
     */
    fun testSetValuesNeverNotifiesListenersDueToIdenticalOldAndNewReference() {
        val coll = JTextFieldCollection()
        var invoked = false
        coll.addValuesChangeListener { invoked = true }

        coll.setValues(listOf("x", "y"))

        assertFalse(invoked)
        assertEquals(listOf("x", "y"), internalValues(coll))
    }

    fun testAddRowButtonAppendsEmptyValueAndFiresChange() {
        val coll = JTextFieldCollection()
        coll.setValues(listOf("a"))

        var oldSeen: List<String>? = null
        var newSeen: List<String>? = null
        coll.addValuesChangeListener { e ->
            @Suppress("UNCHECKED_CAST")
            oldSeen = ArrayList(e.oldValue as List<String>)

            @Suppress("UNCHECKED_CAST")
            newSeen = ArrayList(e.newValue as List<String>)
        }

        val addButton = actionButtons(coll).first { it.action.templatePresentation.text == "Add Row" }
        addButton.action.actionPerformed(TestActionEvent.createTestEvent(addButton.action))

        assertEquals(listOf("a"), oldSeen)
        assertEquals(listOf("a", ""), newSeen)
        assertEquals(listOf("a", ""), internalValues(coll))

        val fields = textFields(coll)
        assertEquals(2, fields.size)
        assertEquals("", fields[1].text)
    }

    fun testRemoveRowButtonRemovesValueAndFiresChange() {
        val coll = JTextFieldCollection()
        coll.setValues(listOf("a", "b", "c"))

        var oldSeen: List<String>? = null
        var newSeen: List<String>? = null
        coll.addValuesChangeListener { e ->
            @Suppress("UNCHECKED_CAST")
            oldSeen = ArrayList(e.oldValue as List<String>)

            @Suppress("UNCHECKED_CAST")
            newSeen = ArrayList(e.newValue as List<String>)
        }

        val removeButtons = actionButtons(coll).filter { it.action.templatePresentation.text == "Remove Row" }
        assertEquals(3, removeButtons.size)

        // Remove the middle row (index 1, value "b").
        removeButtons[1].action.actionPerformed(TestActionEvent.createTestEvent(removeButtons[1].action))

        assertEquals(listOf("a", "b", "c"), oldSeen)
        assertEquals(listOf("a", "c"), newSeen)
        assertEquals(listOf("a", "c"), internalValues(coll))

        val fields = textFields(coll)
        assertEquals(2, fields.size)
        assertEquals("a", fields[0].text)
        assertEquals("c", fields[1].text)
    }

    fun testTypingInRowFieldFiresInsertUpdateAndPropagatesFullRowText() {
        val coll = JTextFieldCollection()
        coll.setValues(listOf("a", "bb"))

        var oldSeen: List<String>? = null
        var newSeen: List<String>? = null
        coll.addValuesChangeListener { e ->
            @Suppress("UNCHECKED_CAST")
            oldSeen = ArrayList(e.oldValue as List<String>)

            @Suppress("UNCHECKED_CAST")
            newSeen = ArrayList(e.newValue as List<String>)
        }

        val field = textFields(coll)[0]
        field.document.insertString(field.document.length, "1", null)

        assertEquals(listOf("a", "bb"), oldSeen)
        assertEquals(listOf("a1", "bb"), newSeen)
        assertEquals(listOf("a1", "bb"), internalValues(coll))
    }

    fun testClearingRowFieldFiresRemoveUpdateAndPropagatesFullRowText() {
        val coll = JTextFieldCollection()
        coll.setValues(listOf("ab", "c"))

        var newSeen: List<String>? = null
        coll.addValuesChangeListener { e ->
            @Suppress("UNCHECKED_CAST")
            newSeen = ArrayList(e.newValue as List<String>)
        }

        val field = textFields(coll)[0]
        field.document.remove(0, 1)

        assertEquals(listOf("b", "c"), newSeen)
        assertEquals(listOf("b", "c"), internalValues(coll))
    }

    fun testChangedUpdateListenerUpdatesRowValue() {
        val coll = JTextFieldCollection()
        coll.setValues(listOf("a", "b"))

        val field = textFields(coll)[0]
        val doc = field.document as AbstractDocument
        val listener = doc.getListeners(DocumentListener::class.java).first()

        var newSeen: List<String>? = null
        coll.addValuesChangeListener { e ->
            @Suppress("UNCHECKED_CAST")
            newSeen = ArrayList(e.newValue as List<String>)
        }

        val fakeEvent =
            object : DocumentEvent {
                override fun getOffset(): Int = 0

                override fun getLength(): Int = 0

                override fun getDocument(): javax.swing.text.Document = doc

                override fun getType(): DocumentEvent.EventType = DocumentEvent.EventType.CHANGE

                override fun getChange(elem: Element?): DocumentEvent.ElementChange? = null
            }

        listener.changedUpdate(fakeEvent)

        // The document text is unchanged (this is only a synthetic "attributes changed"
        // notification), so the row value derived from it is identical to what it already was.
        // Since `newValues` (a fresh List) is content-equal to `values`, PropertyChangeSupport's
        // `equals` check suppresses delivery - assert on internal state instead of the listener.
        assertNull(newSeen)
        assertEquals(listOf("a", "b"), internalValues(coll))
    }

    fun testAddValuesChangeListenerCanBeRegisteredMultipleTimes() {
        val coll = JTextFieldCollection()
        var firstInvoked = false
        var secondInvoked = false
        coll.addValuesChangeListener { firstInvoked = true }
        coll.addValuesChangeListener { secondInvoked = true }

        coll.setValues(listOf("a"))
        val addButton = actionButtons(coll).first { it.action.templatePresentation.text == "Add Row" }
        addButton.action.actionPerformed(TestActionEvent.createTestEvent(addButton.action))

        assertTrue(firstInvoked)
        assertTrue(secondInvoked)
    }
}
