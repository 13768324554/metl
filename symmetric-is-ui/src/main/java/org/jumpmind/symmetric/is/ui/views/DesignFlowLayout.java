package org.jumpmind.symmetric.is.ui.views;

import java.util.List;

import org.jumpmind.symmetric.is.core.model.Component;
import org.jumpmind.symmetric.is.core.model.ComponentVersion;
import org.jumpmind.symmetric.is.core.model.FlowStep;
import org.jumpmind.symmetric.is.core.model.FlowStepLink;
import org.jumpmind.symmetric.is.core.model.FlowVersion;
import org.jumpmind.symmetric.is.core.persist.IConfigurationService;
import org.jumpmind.symmetric.is.core.runtime.component.IComponentFactory;
import org.jumpmind.symmetric.is.ui.common.IBackgroundRefreshable;
import org.jumpmind.symmetric.is.ui.diagram.Diagram;
import org.jumpmind.symmetric.is.ui.diagram.Node;
import org.jumpmind.symmetric.is.ui.diagram.NodeMovedEvent;
import org.jumpmind.symmetric.is.ui.diagram.NodeSelectedEvent;
import org.jumpmind.symmetric.is.ui.diagram.ResourceEvent;
import org.jumpmind.symmetric.is.ui.init.BackgroundRefresherService;
import org.jumpmind.symmetric.ui.common.IUiPanel;

import com.vaadin.ui.CssLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Panel;
import com.vaadin.ui.themes.ValoTheme;

public class DesignFlowLayout extends HorizontalLayout implements IUiPanel, IBackgroundRefreshable {

    private static final long serialVersionUID = 1L;

    IConfigurationService configurationService;

    FlowVersion componentFlowVersion;

    DesignPropertySheet designPropertySheet;

    DesignNavigator designNavigator;

    Diagram diagram;
    
    CssLayout diagramLayout;

    BackgroundRefresherService backgroundRefresherService;

    public DesignFlowLayout(BackgroundRefresherService backgroundRefresherService, IComponentFactory componentFactory,
            IConfigurationService configurationService, FlowVersion componentFlowVersion,
            DesignPropertySheet designPropertySheet,
            DesignNavigator designNavigator) {
        this.backgroundRefresherService = backgroundRefresherService;
        this.configurationService = configurationService;
        this.componentFlowVersion = componentFlowVersion;
        this.designPropertySheet = designPropertySheet;
        this.designNavigator = designNavigator;

        DesignComponentPalette designComponentPalette = new DesignComponentPalette(this, componentFactory);
        addComponent(designComponentPalette);

        diagramLayout = new CssLayout();
        diagramLayout.setWidth(10000, Unit.PIXELS);
        diagramLayout.setHeight(10000, Unit.PIXELS);
        
        Panel panel = new Panel();
        panel.setSizeFull();
        panel.addStyleName(ValoTheme.PANEL_WELL);
        addComponent(panel);
        setExpandRatio(panel, 1);
        
        panel.setContent(diagramLayout);
        
        redrawFlow();
        backgroundRefresherService.register(this);
    }

    @Override
    public void onBackgroundUIRefresh(Object backgroundData) {
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object onBackgroundDataRefresh() {
        return null;
    }

    @Override
    public boolean closing() {
        backgroundRefresherService.unregister(this);
        return true;
    }

    @Override
    public void showing() {
    }

    protected int countComponentsOfType(String type) {
        int count = 0;
        List<FlowStep> nodes = componentFlowVersion.getFlowSteps();
        for (FlowStep componentflowStep : nodes) {
            if (componentflowStep.getComponentVersion().getComponent().getType().equals(type)) {
                count++;
            }
        }
        return count;
    }

    protected void addComponent(Component component) {
        ComponentVersion componentVersion = new ComponentVersion(component);
        componentVersion.setVersionName("version 1.0");

        component.setName(component.getType() + " "
                + (countComponentsOfType(component.getType()) + 1));

        FlowStep componentflowStep = new FlowStep(componentVersion);
        componentflowStep.setFlowVersionId(componentFlowVersion.getId());
        componentFlowVersion.getFlowSteps().add(componentflowStep);

        configurationService.save(componentflowStep);

        redrawFlow();

        designPropertySheet.valueChange(componentVersion);

        designNavigator.refresh();

    }

    protected void redrawFlow() {
        if (diagram != null) {
            diagramLayout.removeComponent(diagram);
        }

        diagram = new Diagram();
        diagram.addListener(new DiagramChangedListener());
        diagramLayout.addComponent(diagram);

        List<FlowStepLink> links = componentFlowVersion.getFlowStepLinks();

        List<FlowStep> flowSteps = componentFlowVersion.getFlowSteps();
        for (FlowStep flowStep : flowSteps) {
            Node node = new Node();
            String name = flowStep.getComponentVersion().getComponent().getName();
            String type = flowStep.getComponentVersion().getComponent().getType();
            node.setText(name + "<br><i>" + type + "</i>");
            node.setId(flowStep.getId());
            node.setX(flowStep.getX());
            node.setY(flowStep.getY());
            diagram.addNode(node);

            for (FlowStepLink link : links) {
                if (link.getSourceStepId().equals(node.getId())) {
                    node.getTargetNodeIds().add(link.getTargetStepId());
                }
            }

        }

    }

    class DiagramChangedListener implements Listener {
        private static final long serialVersionUID = 1L;

        @Override
        public void componentEvent(Event e) {
            if (e instanceof NodeSelectedEvent) {
                NodeSelectedEvent event = (NodeSelectedEvent) e;
                Node node = event.getNode();
                FlowStep flowStep = componentFlowVersion.findFlowStepWithId(node.getId());
                designPropertySheet.valueChange(flowStep.getComponentVersion());
                designNavigator.select(flowStep);

            } else if (e instanceof NodeMovedEvent) {
                NodeMovedEvent event = (NodeMovedEvent) e;
                Node node = event.getNode();
                FlowStep flowStep = componentFlowVersion.findFlowStepWithId(node.getId());
                if (flowStep != null) {
                    flowStep.setX(node.getX());
                    flowStep.setY(node.getY());
                }
                configurationService.save(componentFlowVersion);

            } else if (e instanceof ResourceEvent) {
                ResourceEvent event = (ResourceEvent) e;
                if (!event.isRemoved()) {
                    componentFlowVersion.getFlowStepLinks().add(
                            new FlowStepLink(event.getSourceNodeId(), event.getTargetNodeId()));
                    configurationService.save(componentFlowVersion);
                } else {
                    FlowStepLink link = componentFlowVersion.removeFlowStepLink(
                            event.getSourceNodeId(), event.getTargetNodeId());
                    if (link != null) {
                        configurationService.delete(link);
                    }

                }
            }
        }
    }

}
