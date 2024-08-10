@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.github.sam0delkin.intellijpsa.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.dsl.builder.*
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class JTextFieldCollection : JBPanel<DialogPanel>() {
    private val pcs = PropertyChangeSupport(this)
    private val values = ArrayList<String>()

    init {
        this.updatePanel()
    }

    fun setValues(newValues: List<String>) {
        this.values.clear()
        this.values.addAll(newValues)
        this.pcs.firePropertyChange("values", values, values)

        this.updatePanel()
    }

    private fun updatePanel() {
        val self = this
        this.removeAll()
        val innerPanel =
            panel {
                values.forEachIndexed { index, el ->
                    row(index.toString()) {
                        val textField = textField()
                        textField.component.text = el
                        textField.component.document.addDocumentListener(
                            object : DocumentListener {
                                override fun insertUpdate(e: DocumentEvent) {
                                    val newValues = ArrayList<String>(values)
                                    newValues[index] = e.document.getText(0, e.document.length)
                                    self.pcs.firePropertyChange("values", values, newValues)
                                    values.clear()
                                    values.addAll(newValues)
                                }

                                override fun removeUpdate(e: DocumentEvent) {
                                    val newValues = ArrayList<String>(values)
                                    newValues[index] = e.document.getText(0, e.document.length)
                                    self.pcs.firePropertyChange("values", values, newValues)
                                    values.clear()
                                    values.addAll(newValues)
                                }

                                override fun changedUpdate(e: DocumentEvent) {
                                    val newValues = ArrayList<String>(values)
                                    newValues[index] = e.document.getText(0, e.document.length)
                                    self.pcs.firePropertyChange("values", values, newValues)
                                    values.clear()
                                    values.addAll(newValues)
                                }
                            },
                        )
                        actionButton(
                            object : DumbAwareAction("Remove Row", "", AllIcons.General.Remove) {
                                override fun actionPerformed(e: AnActionEvent) {
                                    val newValues = ArrayList<String>(values)
                                    newValues.removeAt(index)
                                    self.pcs.firePropertyChange("values", values, newValues)
                                    values.clear()
                                    values.addAll(newValues)
                                    updatePanel()
                                }
                            },
                        )
                    }
                }
                row {
                    actionButton(
                        object : DumbAwareAction("Add Row", "", AllIcons.General.Add) {
                            override fun actionPerformed(e: AnActionEvent) {
                                val newValues = ArrayList<String>(values)
                                newValues.add("")
                                self.pcs.firePropertyChange("values", values, newValues)
                                values.clear()
                                values.addAll(newValues)
                                updatePanel()
                            }
                        },
                    )
                }
            }
        this.add(innerPanel)
        this.revalidate()
        this.repaint()
    }

    fun addValuesChangeListener(listener: PropertyChangeListener?) {
        this.pcs.addPropertyChangeListener("values", listener)
    }
}
