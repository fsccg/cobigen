/*******************************************************************************
 * Copyright © Capgemini 2013. All rights reserved.
 ******************************************************************************/
package com.capgemini.cobigen.eclipse.wizard.common;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TreeItem;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.capgemini.cobigen.config.entity.Template;
import com.capgemini.cobigen.eclipse.Activator;
import com.capgemini.cobigen.eclipse.generator.java.JavaGeneratorWrapper;
import com.capgemini.cobigen.eclipse.generator.java.entity.ComparableIncrement;
import com.capgemini.cobigen.eclipse.wizard.common.control.ButtonListener;
import com.capgemini.cobigen.eclipse.wizard.common.control.CheckStateListener;
import com.capgemini.cobigen.eclipse.wizard.common.model.PackagesContentProvider;
import com.capgemini.cobigen.eclipse.wizard.common.model.SelectFileContentProvider;
import com.capgemini.cobigen.eclipse.wizard.common.model.SelectFileLabelProvider;
import com.capgemini.cobigen.eclipse.wizard.common.widget.CustomizedCheckboxTreeViewer;
import com.capgemini.cobigen.eclipse.wizard.common.widget.SimulatedCheckboxTreeViewer;
import com.capgemini.cobigen.extension.to.IncrementTo;
import com.capgemini.cobigen.extension.to.TemplateTo;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * The {@link SelectFilesPage} displays a resource tree of all resources that may be change by the generation
 * process
 *
 * @author mbrunnli (14.02.2013)
 */
public class SelectFilesPage extends WizardPage {

    /**
     * {@link TreeViewer} of the simulated generation targets
     */
    private CheckboxTreeViewer resourcesTree;

    /**
     * List of generation packages
     */
    private CheckboxTreeViewer packageSelector;

    /**
     * Container holding the right site of the UI, containing a label and the resources tree
     */
    private Composite containerRight;

    /**
     * Checkbox for "Remember selection" functionality
     */
    private Button rememberSelection;

    /**
     * Current used {@link JavaGeneratorWrapper} instance
     */
    private JavaGeneratorWrapper javaGeneratorWrapper;

    /**
     * Defines whether the {@link JavaGeneratorWrapper} is in batch mode.
     */
    private boolean batch;

    /**
     * Possible check states
     */
    public static enum CHECK_STATE {
        /**
         * checked
         */
        CHECKED,
        /**
         * unchecked
         */
        UNCHECKED
    }

    /**
     * Assigning logger to SelectFilesPage
     */
    private final static Logger LOG = LoggerFactory.getLogger(SelectFilesPage.class);

    /**
     * Creates a new {@link SelectFilesPage} which displays a resource tree of all resources that may be
     * change by the generation process
     *
     * @param javaGeneratorWrapper
     *            the {@link JavaGeneratorWrapper} instance
     * @param batch
     *            states whether the generation will run in batch mode
     * @author mbrunnli (14.02.2013)
     */
    public SelectFilesPage(JavaGeneratorWrapper javaGeneratorWrapper, boolean batch) {

        super("Generate");
        setTitle("Select the Resources, which should be generated.");
        this.javaGeneratorWrapper = javaGeneratorWrapper;
        this.batch = batch;
    }

    /**
     * {@inheritDoc}
     *
     * @author mbrunnli (14.02.2013)
     */
    @Override
    public void createControl(Composite parent) {

        Composite container = new Composite(parent, SWT.FILL);
        container.setLayout(new GridLayout());

        SashForm sash = new SashForm(container, SWT.HORIZONTAL);
        GridData gd = new GridData(GridData.FILL_BOTH);
        gd.grabExcessHorizontalSpace = true;
        gd.grabExcessVerticalSpace = true;
        sash.setLayoutData(gd);

        Composite containerLeft = new Composite(sash, SWT.FILL);
        containerLeft.setLayout(new GridLayout(1, false));

        Label label = new Label(containerLeft, SWT.NONE);
        label.setText("Filter (generation packages):");

        this.packageSelector = new CustomizedCheckboxTreeViewer(containerLeft);
        this.packageSelector.setContentProvider(new PackagesContentProvider());
        this.packageSelector.setInput(this.javaGeneratorWrapper.getAllIncrements());
        gd = new GridData(GridData.FILL_BOTH);
        gd.grabExcessVerticalSpace = true;
        this.packageSelector.getTree().setLayoutData(gd);
        this.packageSelector.expandAll();

        this.containerRight = new Composite(sash, SWT.FILL);
        this.containerRight.setLayout(new GridLayout(1, false));

        boolean initiallyCustomizable = false;
        buildResourceTreeViewer(initiallyCustomizable);

        CheckStateListener checkListener =
            new CheckStateListener(this.javaGeneratorWrapper, this, this.batch);
        this.packageSelector.addCheckStateListener(checkListener);

        sash.setWeights(new int[] { 1, 3 });

        this.rememberSelection = new Button(container, SWT.CHECK);
        this.rememberSelection.setText("Remember my selection");
        gd = new GridData();
        gd.horizontalAlignment = SWT.BEGINNING;
        this.rememberSelection.setLayoutData(gd);
        this.rememberSelection.addSelectionListener(checkListener);

        Button but = new Button(container, SWT.PUSH);
        but.setText("Customize");
        gd = new GridData();
        gd.horizontalAlignment = SWT.END;
        but.setLayoutData(gd);
        but.addListener(SWT.Selection, new ButtonListener(initiallyCustomizable, this));

        setControl(container);
        loadSelection();
    }

    /**
     * Returns the resources tree
     *
     * @return current {@link CheckboxTreeViewer} instance
     * @author mbrunnli (12.03.2013)
     */
    public CheckboxTreeViewer getResourcesTree() {

        return this.resourcesTree;
    }

    /**
     * Returns the package selector
     *
     * @return current {@link CheckboxTableViewer} instance
     * @author mbrunnli (12.03.2013)
     */
    public CheckboxTreeViewer getPackageSelector() {

        return this.packageSelector;
    }

    /**
     * Disposes all children of the container control which holds the resource tree
     *
     * @author mbrunnli (12.03.2013)
     */
    private void disposeContainerRightChildren() {

        for (Control c : this.containerRight.getChildren()) {
            c.dispose();
        }
    }

    /**
     * Builds the {@link TreeViewer} providing the tree of resources to be generated
     *
     * @param customizable
     *            states whether the checkboxes of the tree should be displayed or not
     * @author mbrunnli (12.03.2013)
     */
    public void buildResourceTreeViewer(boolean customizable) {

        IContentProvider cp;
        IBaseLabelProvider lp;
        Object[] checkedElements;
        if (this.resourcesTree != null) {
            cp = this.resourcesTree.getContentProvider();
            lp = this.resourcesTree.getLabelProvider();
            checkedElements = this.resourcesTree.getCheckedElements();
        } else {
            cp = new SelectFileContentProvider();
            lp = new SelectFileLabelProvider(this.javaGeneratorWrapper, this.batch);
            checkedElements = new Object[0];
        }

        disposeContainerRightChildren();

        Label label = new Label(this.containerRight, SWT.NONE);
        label.setText("Resources to be generated (selected):");

        if (customizable) {
            this.resourcesTree = new CustomizedCheckboxTreeViewer(this.containerRight);
        } else {
            this.resourcesTree = new SimulatedCheckboxTreeViewer(this.containerRight);
        }

        this.resourcesTree.setContentProvider(cp);
        this.resourcesTree.setLabelProvider(lp);
        this.resourcesTree
            .setInput(new IProject[] { this.javaGeneratorWrapper.getGenerationTargetProject() });
        this.resourcesTree.expandToLevel(TreeViewer.ALL_LEVELS);
        GridData gd = new GridData(GridData.FILL_BOTH);
        gd.grabExcessHorizontalSpace = true;
        gd.grabExcessVerticalSpace = true;
        this.resourcesTree.getTree().setLayoutData(gd);

        CheckStateListener listener = new CheckStateListener(this.javaGeneratorWrapper, this, this.batch);
        this.resourcesTree.addCheckStateListener(listener);
        this.resourcesTree.setCheckedElements(checkedElements);

        this.containerRight.layout();
    }

    /**
     * {@inheritDoc}
     *
     * @author mbrunnli (28.04.2013)
     */
    @Override
    public boolean canFlipToNextPage() {

        return this.resourcesTree.getCheckedElements().length > 0;
    }

    /**
     * Returns the set of all paths to files which should be generated
     *
     * @return the set of all paths to files which should be generated
     * @author mbrunnli (14.02.2013)
     */
    public Set<String> getFilePathsToBeGenerated() {

        Set<String> filesToBeGenerated = new HashSet<>();
        Object[] checkedElements = this.resourcesTree.getCheckedElements();
        for (Object e : checkedElements) {
            if (e instanceof IJavaElement) {
                filesToBeGenerated.add(((IJavaElement) e).getPath().toString());
            } else if (e instanceof IResource) {
                filesToBeGenerated.add(((IResource) e).getFullPath().toString());
            }
        }
        return filesToBeGenerated;
    }

    /**
     * Returns a {@link Set} containing the {@link Template}s, that are included in the selected
     * {@link ComparableIncrement}s
     *
     * @return the {@link Set} of {@link Template}s
     * @author trippl (24.04.2013)
     */
    public List<TemplateTo> getTemplatesToBeGenerated() {

        Set<IncrementTo> selectedIncrements = Sets.newHashSet();
        for (Object checkedElement : this.packageSelector.getCheckedElements()) {
            if (checkedElement instanceof IncrementTo) selectedIncrements.add((IncrementTo) checkedElement);
        }

        List<TemplateTo> templates = Lists.newLinkedList();
        for (String path : getFilePathsToBeGenerated()) {
            templates.addAll(this.javaGeneratorWrapper.getTemplatesForFilePath(path, selectedIncrements));
        }
        return templates;
    }

    /**
     * Returns all selected {@link IResource}s of the {@link TreeViewer}
     *
     * @return all selected {@link IResource}s of the {@link TreeViewer}
     * @author mbrunnli (18.02.2013)
     */
    public List<Object> getSelectedResources() {

        List<Object> selectedResources = new LinkedList<>();
        Object[] checkedElements = this.resourcesTree.getCheckedElements();
        for (Object e : checkedElements) {
            if (e instanceof IJavaElement || e instanceof IResource) {
                selectedResources.add(e);
            }
        }
        return selectedResources;
    }

    /**
     * Loads the last package selection
     *
     * @author trippl (18.04.2013)
     */
    private void loadSelection() {

        IEclipsePreferences preferences = ConfigurationScope.INSTANCE.getNode(Activator.PLUGIN_ID);
        Preferences selection = preferences.node("selection");

        TreeItem[] items = this.packageSelector.getTree().getItems();
        for (int i = 0; i < items.length; i++) {
            ComparableIncrement element = (ComparableIncrement) items[i].getData();
            if (element.getTriggerId() != null) {
                String value =
                    selection.node(element.getTriggerId()).get(element.getId(), CHECK_STATE.UNCHECKED.name());
                if (value.equals(CHECK_STATE.CHECKED.name())) {
                    this.packageSelector.setChecked(element, true);
                }
            } else if (element.getId().equals("all")) {
                String value = selection.node("All").get(element.getId(), CHECK_STATE.UNCHECKED.name());
                if (value.equals(CHECK_STATE.CHECKED.name())) {
                    this.packageSelector.setChecked(element, true);
                }
            }
        }
    }

    /**
     * Saves the current package selection
     *
     * @author trippl (18.04.2013)
     */
    public void saveSelection() {

        if (!this.rememberSelection.getSelection()) // only save Selection if intended
            return;
        IEclipsePreferences preferences = ConfigurationScope.INSTANCE.getNode(Activator.PLUGIN_ID);
        Preferences selection = preferences.node("selection");

        TreeItem[] items = this.packageSelector.getTree().getItems();
        for (int i = 0; i < items.length; i++) {
            ComparableIncrement element = (ComparableIncrement) items[i].getData();
            if (element.getTriggerId() != null) {
                if (items[i].getChecked()) {
                    selection.node(element.getTriggerId()).put(element.getId(), CHECK_STATE.CHECKED.name());
                } else {
                    selection.node(element.getTriggerId()).put(element.getId(), CHECK_STATE.UNCHECKED.name());
                }
            } else if (element.getId().equals("all")) {
                if (items[i].getChecked()) {
                    selection.node("All").put(element.getId(), CHECK_STATE.CHECKED.name());
                } else {
                    selection.node("All").put(element.getId(), CHECK_STATE.UNCHECKED.name());
                }
            }
        }

        try {
            preferences.flush();
        } catch (BackingStoreException e) {
            LOG.error("Error while flushing last selection into preferences.", e);
        }
    }

    /**
     * Checks whether the "rememberSelection" box is checked
     *
     * @return <code>true</code> if "rememberSelection" is enabled<br>
     *         <code>false</code> otherwise
     * @author mbrunnli (28.04.2013)
     */
    public boolean isSetRememberSelection() {

        if (this.rememberSelection != null) {
            return this.rememberSelection.getSelection();
        }
        return false;
    }

}
