/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2021 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Created on Jul 19, 2004
 */
package org.jkiss.dbeaver.erd.ui.editor;

import org.eclipse.gef.*;
import org.eclipse.gef.palette.PaletteContainer;
import org.eclipse.gef.palette.PaletteDrawer;
import org.eclipse.gef.palette.PaletteRoot;
import org.eclipse.gef.palette.ToolEntry;
import org.eclipse.gef.tools.SelectionTool;
import org.eclipse.gef.ui.parts.AbstractEditPartViewer;
import org.eclipse.gef.ui.parts.ScrollingGraphicalViewer;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.themes.ITheme;
import org.eclipse.ui.themes.IThemeManager;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.erd.model.ERDEntity;
import org.jkiss.dbeaver.erd.model.ERDEntityAttribute;
import org.jkiss.dbeaver.erd.model.ERDObject;
import org.jkiss.dbeaver.erd.model.ERDUtils;
import org.jkiss.dbeaver.erd.ui.ERDUIConstants;
import org.jkiss.dbeaver.erd.ui.directedit.ValidationMessageHandler;
import org.jkiss.dbeaver.erd.ui.model.EntityDiagram;
import org.jkiss.dbeaver.erd.ui.part.DiagramPart;
import org.jkiss.dbeaver.erd.ui.part.EntityPart;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntity;
import org.jkiss.dbeaver.model.struct.DBSEntityAttribute;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.DBeaverIcons;
import org.jkiss.dbeaver.ui.UIUtils;

import java.util.*;

/**
 * GraphicalViewer which also knows about ValidationMessageHandler to output
 * error messages to
 * @author Serge Rider
 */
public class ERDGraphicalViewer extends ScrollingGraphicalViewer implements IPropertyChangeListener, DBPEventListener {
    private static final Log log = Log.getLog(ERDGraphicalViewer.class);

    private ERDEditorPart editor;
	private ValidationMessageHandler messageHandler;
    private IThemeManager themeManager;
    private boolean loadContents = false;

    private static class DataSourceInfo {
        int tableCount = 0;
    }

    private final Map<DBPDataSourceContainer, DataSourceInfo> usedDataSources = new IdentityHashMap<>();

	/**
	 * ValidationMessageHandler to receive messages
	 * @param messageHandler message handler 
	 */
	public ERDGraphicalViewer(ERDEditorPart editor, ValidationMessageHandler messageHandler)
	{
		super();
        this.editor = editor;
		this.messageHandler = messageHandler;

        themeManager = editor.getSite().getWorkbenchWindow().getWorkbench().getThemeManager();
        themeManager.addPropertyChangeListener(this);

        setProperty(MouseWheelHandler.KeyGenerator.getKey(SWT.MOD1), MouseWheelZoomHandler.SINGLETON);
        //setProperty(MouseWheelHandler.KeyGenerator.getKey(SWT.MOD2), MouseWheelHorizontalScrollHandler.SINGLETON);
    }

    public ERDEditorPart getEditor()
    {
        return editor;
    }

    @Override
    public void setControl(Control control)
    {
        super.setControl(control);

        if (control != null) {
            ERDEditorAdapter.mapControl(control, editor);
            UIUtils.addFocusTracker(editor.getSite(), ERDUIConstants.ERD_CONTROL_ID, control);
            applyThemeSettings();
        }
    }

    @Override
    protected void handleDispose(DisposeEvent e) {
        if (themeManager != null) {
            themeManager.removePropertyChangeListener(this);
        }
        if (getControl() != null) {
            ERDEditorAdapter.unmapControl(getControl());
        }
        super.handleDispose(e);
    }

    /**
	 * @return Returns the messageLabel.
	 */
	public ValidationMessageHandler getValidationHandler()
	{
		return messageHandler;
	}

	/**
	 * This method is invoked when this viewer's control loses focus. It removes
	 * focus from the {@link AbstractEditPartViewer#focusPart focusPart}, if
	 * there is one.
	 * 
	 * @param fe
	 *            the focusEvent received by this viewer's control
	 */
	@Override
    protected void handleFocusLost(FocusEvent fe)
	{
		//give the superclass a chance to handle this first
		super.handleFocusLost(fe);
		//call reset on the MessageHandler itself
		messageHandler.reset();
	}

    @Override
    public void propertyChange(PropertyChangeEvent event)
    {
        if (event.getProperty().equals(IThemeManager.CHANGE_CURRENT_THEME)
            || event.getProperty().equals(ERDUIConstants.PROP_DIAGRAM_FONT))
        {
            applyThemeSettings();
        }
    }

    private void applyThemeSettings()
    {
        ITheme currentTheme = themeManager.getCurrentTheme();
        Font erdFont = currentTheme.getFontRegistry().get(ERDUIConstants.PROP_DIAGRAM_FONT);
        if (erdFont != null) {
            this.getControl().setFont(erdFont);
        }
        editor.refreshDiagram(true, false);
/*
        DiagramPart diagramPart = editor.getDiagramPart();
        if (diagramPart != null) {
            diagramPart.resetFonts();
            diagramPart.refresh();
        }
*/
    }

    @Override
    public void setContents(EditPart editpart)
    {
        loadContents = true;
        try {
            super.setContents(editpart);
            // Reset palette contents
            if (editpart instanceof DiagramPart) {
                List<DBSEntity> tables = new ArrayList<>();
                for (Object child : editpart.getChildren()) {
                    if (child instanceof EntityPart) {
                        tables.add(((EntityPart) child).getEntity().getObject());
                    }
                }
                tables.sort(DBUtils.nameComparator());
                Map<PaletteDrawer, List<ToolEntryTable>> toolMap = new LinkedHashMap<>();
                for (DBSEntity table : tables) {
                    DBPDataSourceContainer container = table.getDataSource().getContainer();
                    PaletteDrawer drawer = getContainerPaletteDrawer(container);
                    if (drawer != null) {
                        List<ToolEntryTable> tools = toolMap.get(drawer);
                        if (tools == null) {
                            tools = new ArrayList<>(tables.size());
                            toolMap.put(drawer, tools);
                        }
                        tools.add(new ToolEntryTable(table));
                    }
                }
                for (Map.Entry<PaletteDrawer, List<ToolEntryTable>> entry : toolMap.entrySet()) {
                    entry.getKey().setChildren(entry.getValue());
                }
                //editor.getPaletteContents().setChildren(tools);
            }
        }
        finally {
            loadContents = false;
        }
    }

    public void handleTableActivate(DBSEntity table)
    {
        if (table.getDataSource() != null) {
            DBPDataSourceContainer container = table.getDataSource().getContainer();
            if (container != null) {
                synchronized (usedDataSources) {
                    DataSourceInfo dataSourceInfo = usedDataSources.get(container);
                    if (dataSourceInfo == null) {
                        dataSourceInfo = new DataSourceInfo();
                        usedDataSources.put(container, dataSourceInfo);
                        acquireContainer(container);
                    }
                    dataSourceInfo.tableCount++;
                }
            }
        }

        if (!loadContents) {
            final PaletteContainer drawer = getContainerPaletteDrawer(table.getDataSource().getContainer());
            if (drawer != null) {
                // Add entry (with right order)
                List children = drawer.getChildren();
                int index = 0;
                for (int i = 0, childrenSize = children.size(); i < childrenSize; i++) {
                    Object child = children.get(i);
                    if (child instanceof ToolEntryTable) {
                        if (((ToolEntryTable) child).table.getName().compareTo(table.getName()) > 0) {
                            index = i;
                            break;
                        }
                    }
                }
                drawer.add(index, new ToolEntryTable(table));
            }
        }
    }

    public void handleTableDeactivate(DBSEntity table)
    {
        final PaletteContainer drawer = getContainerPaletteDrawer(table.getDataSource().getContainer());
        if (drawer != null) {
            for (Object entry : drawer.getChildren()) {
                if (entry instanceof ToolEntryTable && ((ToolEntryTable)entry).table == table) {
                    drawer.remove((ToolEntryTable)entry);
                    break;
                }
            }
        }
        if (table.getDataSource() != null) {
            DBPDataSourceContainer container = table.getDataSource().getContainer();
            if (container != null) {
                synchronized (usedDataSources) {
                    DataSourceInfo dataSourceInfo = usedDataSources.get(container);
                    if (dataSourceInfo == null) {
                        log.warn("Datasource '" + container + "' not registered in ERD viewer");
                    } else {
                        dataSourceInfo.tableCount--;
                        if (dataSourceInfo.tableCount <= 0) {
                            usedDataSources.remove(container);
                            releaseContainer(container);
                        }
                    }
                }
            }
        }
    }

    private void acquireContainer(DBPDataSourceContainer container)
    {
        container.acquire(editor);
        container.getRegistry().addDataSourceListener(this);

        PaletteRoot paletteRoot = editor.getPaletteRoot();

        PaletteDrawer dsDrawer = new PaletteDrawer(
            container.getName(),
            DBeaverIcons.getImageDescriptor(container.getDriver().getIcon()));
        dsDrawer.setDescription(container.getDescription());
        dsDrawer.setId(container.getId());

        paletteRoot.add(dsDrawer);
    }

    private void releaseContainer(DBPDataSourceContainer container)
    {
        PaletteDrawer drawer = getContainerPaletteDrawer(container);
        if (drawer != null) {
            editor.getPaletteRoot().remove(drawer);
        }

        container.getRegistry().removeDataSourceListener(this);
        container.release(editor);
    }

    PaletteDrawer getContainerPaletteDrawer(DBPDataSourceContainer container)
    {
        for (Object child : editor.getPaletteRoot().getChildren()) {
            if (child instanceof PaletteDrawer && container.getId().equals(((PaletteDrawer) child).getId())) {
                return (PaletteDrawer) child;
            }
        }
        return null;
    }

    @Override
    public void handleDataSourceEvent(DBPEvent event)
    {
        DBSObject object = event.getObject();
        if (object == null || DBWorkbench.getPlatform().isShuttingDown()) {
            return;
        }
        if (object instanceof DBPDataSourceContainer) {
            handleDataSourceContainerChange(event, (DBPDataSourceContainer) object);
            return;
        }

        // Object change
        DBPEvent.Action action = event.getAction();
        if (action == DBPEvent.Action.OBJECT_SELECT || !usedDataSources.containsKey(object.getDataSource().getContainer())) {
            return;
        }
        DBSEntity entity;
        DBSEntityAttribute entityAttribute;
        if (object instanceof DBSEntityAttribute) {
            entityAttribute = (DBSEntityAttribute) object;
            entity = entityAttribute.getParentObject();
        } else if (object instanceof DBSEntity) {
            entityAttribute = null;
            entity = (DBSEntity) object;
        } else {
            return;
        }

        EntityDiagram diagram = editor.getDiagram();
        switch (action) {
            case OBJECT_ADD: {
                if (entityAttribute != null) {
                    // New attribute
                    ERDEntity erdEntity = diagram.getEntity(entity);
                    if (erdEntity != null) {
                        UIUtils.asyncExec(() -> {
                            erdEntity.reloadAttributes(diagram);
                            erdEntity.firePropertyChange(ERDEntity.PROP_CONTENTS, null, null);
                        });
                    }

                } else {
                    // New entity. Add it if it has the same object container
                    // or if this entity was created from the same editor
                    DBSObject diagramContainer = diagram.getObject();
                    if (diagramContainer == entity.getParentObject()) {

                        ERDEntity erdEntity = ERDUtils.makeEntityFromObject(
                            new VoidProgressMonitor(),
                            diagram,
                            Collections.emptyList(),
                            entity,
                            null);
                        diagram.addEntity(erdEntity, true);
                    }
                }
                break;
            }
            case OBJECT_REMOVE: {
                ERDEntity erdEntity = diagram.getEntity(entity);
                if (erdEntity != null) {
                    UIUtils.asyncExec(() -> {
                        if (entityAttribute == null) {
                            // Entity delete
                            diagram.removeEntity(erdEntity, true);
                        } else {
                            ERDEntityAttribute erdAttribute = erdEntity.getAttribute(entityAttribute);
                            if (erdAttribute != null) {
                                erdEntity.removeAttribute(erdAttribute, false);
                                erdEntity.firePropertyChange(ERDEntity.PROP_CONTENTS, null, null);
                            }
                        }
                    });
                }
                break;
            }
            case OBJECT_UPDATE: {
                ERDEntity erdEntity = diagram.getEntity(entity);
                if (erdEntity != null) {
                    UIUtils.asyncExec(() -> {
                        if (entityAttribute == null) {
                            erdEntity.reloadAttributes(diagram);
                            erdEntity.firePropertyChange(ERDEntity.PROP_CONTENTS, null, null);
                        } else {
                            ERDEntityAttribute erdAttribute = erdEntity.getAttribute(entityAttribute);
                            if (erdAttribute != null) {
                                erdAttribute.firePropertyChange(ERDEntityAttribute.PROP_NAME, null, entityAttribute.getName());
                                // Resize entity
                                erdEntity.firePropertyChange(ERDObject.PROP_SIZE, null, null);
                            }
                        }
                    });
                }
                break;
            }
        }
    }

    private void handleDataSourceContainerChange(DBPEvent event, DBPDataSourceContainer object) {
        DBPDataSourceContainer container = object;
        if (usedDataSources.containsKey(container) &&
            event.getAction() == DBPEvent.Action.OBJECT_UPDATE &&
            Boolean.FALSE.equals(event.getEnabled())) {
            // Close editor only if it is simple disconnect
            // Workbench shutdown doesn't close editor
            UIUtils.asyncExec(() -> {
                IWorkbenchPartSite site = editor.getSite();
                if (site != null && site.getWorkbenchWindow() != null) {
                    site.getWorkbenchWindow().getActivePage().closeEditor(editor, false);
                }
            });
        }
    }

    private static class ToolEntryTable extends ToolEntry {
        private final DBSEntity table;

        ToolEntryTable(DBSEntity table)
        {
            super(table.getName(), table.getDescription(),
                DBeaverIcons.getImageDescriptor(DBIcon.TREE_TABLE),
                DBeaverIcons.getImageDescriptor(DBIcon.TREE_TABLE));
            this.setUserModificationPermission(PERMISSION_NO_MODIFICATION);
            setDescription(DBUtils.getObjectFullName(table, DBPEvaluationContext.UI));
            this.table = table;
        }

        @Override
        public Tool createTool()
        {
            return new ToolSelectTable(table);
        }
    }

    public static class ToolSelectTable extends SelectionTool {

        private final DBSEntity table;

        ToolSelectTable(DBSEntity table)
        {
            this.table = table;
        }

        @Override
        public void activate()
        {
            //ERDGraphicalViewer.this.reveal(part);
            DefaultEditDomain editDomain = (DefaultEditDomain) getDomain();
            final ERDEditorPart editorPart = (ERDEditorPart)editDomain.getEditorPart();
            final GraphicalViewer viewer = editorPart.getViewer();
            for (Object child : editorPart.getDiagramPart().getChildren()) {
                if (child instanceof EntityPart) {
                    if (((EntityPart)child).getEntity().getObject() == table) {
                        viewer.reveal((EditPart) child);
                        viewer.select((EditPart) child);
                        break;
                    }
                }
            }
            super.activate();
        }
    }

}