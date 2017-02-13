package org.jumpmind.metl.ui.views.design;

import java.util.List;

import org.jumpmind.metl.core.model.ProjectVersion;
import org.jumpmind.metl.core.model.ProjectVersionDependency;
import org.jumpmind.metl.core.persist.IConfigurationService;
import org.jumpmind.metl.ui.common.ApplicationContext;
import org.jumpmind.vaadin.ui.common.ResizableWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.ui.Button;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.OptionGroup;
import com.vaadin.ui.Panel;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

public class ChangeDependencyVersionDialog extends ResizableWindow  {

    final Logger log = LoggerFactory.getLogger(getClass());
    private static final long serialVersionUID = 1L;
    private ApplicationContext context;
    IConfigurationService configService;
    OptionGroup optionGroup;

    public ChangeDependencyVersionDialog(ApplicationContext context, Object selectedElement) {
        super("Change Dependency Version");
        this.context = context;
        this.configService = context.getConfigurationService();
        initWindow(selectedElement);
    }

    public static void show(ApplicationContext context, Object selectedElement) {
        ChangeDependencyVersionDialog dialog = new ChangeDependencyVersionDialog(context, selectedElement);
        UI.getCurrent().addWindow(dialog);
    }

    private void initWindow(Object selectedItem) {
        ProjectVersionDependency dependency = (ProjectVersionDependency) selectedItem;
        
        setWidth(400.0f, Unit.PIXELS);
        setHeight(600.0f, Unit.PIXELS);
        VerticalLayout vLayout = new VerticalLayout();
        vLayout.setSizeFull();
        vLayout.setMargin(true);
        ProjectVersion sourceProjectVersion = configService.findProjectVersion(dependency.getProjectVersionId());
        ProjectVersion targetProjectVersion = configService.findProjectVersion(dependency.getTargetProjectVersionId());
        FormLayout form = buildForm(sourceProjectVersion, targetProjectVersion);
        vLayout.addComponent(form);

        Panel projectVersionPanel = new Panel();
        projectVersionPanel.setSizeFull();
        projectVersionPanel.setContent(buildPossibleTargetVersions(targetProjectVersion));
        vLayout.addComponent(projectVersionPanel);
        addComponent(vLayout,1); 
        addComponent(buildButtonBar());
        vLayout.setExpandRatio(projectVersionPanel, 1);       
    }
    
    protected Panel buildPossibleTargetVersions(ProjectVersion targetProjectVersion) {

        Panel possibleTargetVersionsPanel = new Panel("Available Target Versions");        
        possibleTargetVersionsPanel.addStyleName(ValoTheme.PANEL_SCROLL_INDICATOR);
        possibleTargetVersionsPanel.setSizeFull();

        List<ProjectVersion> projectVersions = configService.findProjectVersionsByProject(targetProjectVersion.getProjectId());
        optionGroup = new OptionGroup("Project Version");
        optionGroup.addStyleName("indent");
        for (ProjectVersion version : projectVersions) {
            optionGroup.addStyleName(ValoTheme.OPTIONGROUP_SMALL);
            optionGroup.addItem(version.getVersionLabel());
            if (targetProjectVersion.getId().equalsIgnoreCase(version.getId())) {
                optionGroup.setItemEnabled(version.getVersionLabel(), false);
            }
        }
        possibleTargetVersionsPanel.setContent(optionGroup);
        return possibleTargetVersionsPanel;
    }
    
    protected FormLayout buildForm(ProjectVersion sourceProjectVersion, ProjectVersion targetProjectVersion) {
        FormLayout form = new FormLayout();
        form.addStyleName(ValoTheme.FORMLAYOUT_LIGHT);
        form.setMargin(true);
        TextField sourceProjectNameField = new TextField("Source Project");
        sourceProjectNameField.setValue(sourceProjectVersion.getProject().getName());
        sourceProjectNameField.setEnabled(false);
        form.addComponent(sourceProjectNameField);
        TextField targetProjectNameField = new TextField("Target Project");
        targetProjectNameField.setValue(targetProjectVersion.getProject().getName());
        targetProjectNameField.setEnabled(false);
        form.addComponent(targetProjectNameField);
        TextField currentDependencyVersion = new TextField("Current Dependency Version");
        currentDependencyVersion.setValue(targetProjectVersion.getVersionLabel());
        currentDependencyVersion.setEnabled(false);
        form.addComponent(currentDependencyVersion);

        return form;
    }

    protected HorizontalLayout buildButtonBar() {
        Button cancelButton = new Button("Cancel", e->cancel());
        Button changeButton = new Button("Change", e->change());
        changeButton.addStyleName(ValoTheme.BUTTON_PRIMARY);
        changeButton.setClickShortcut(KeyCode.ENTER);
        return buildButtonFooter(cancelButton, changeButton);        
    }

    protected void change() {
        String selectedVersion = (String) optionGroup.getValue();
        close();
    }
    
    protected void cancel() {
        close();
    }
    
}
