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
package org.jkiss.dbeaver.tools.transfer.database;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.navigator.DBNDataSource;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.runtime.DBRRunnableContext;
import org.jkiss.dbeaver.model.struct.DBSDataContainer;
import org.jkiss.dbeaver.model.struct.DBSDataManipulator;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectContainer;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.tools.transfer.*;
import org.jkiss.dbeaver.tools.transfer.internal.DTMessages;
import org.jkiss.utils.CommonUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DatabaseConsumerSettings
 */
@SuppressWarnings("unchecked")
public class DatabaseConsumerSettings implements IDataTransferSettings {

    private static final Log log = Log.getLog(DatabaseConsumerSettings.class);

    private String containerNodePath;
    private DBNDatabaseNode containerNode;
    private final Map<DBSDataContainer, DatabaseMappingContainer> dataMappings = new LinkedHashMap<>();
    private boolean openNewConnections = true;
    private boolean useTransactions = true;
    private int commitAfterRows = 10000;
    private boolean transferAutoGeneratedColumns = true;
    private boolean truncateBeforeLoad = false;
    private boolean openTableOnFinish = true;
    private boolean useMultiRowInsert;
    private int multiRowInsertBatch = 100;
    private boolean disableUsingBatches = false;
    private String onDuplicateKeyInsertMethodId;
    private boolean disableReferentialIntegrity;

    private transient Map<String, Object> dialogSettings;

    public DatabaseConsumerSettings() {
    }

    @Nullable
    public DBSObjectContainer getContainer() {
        if (containerNode == null) {
            return null;
        }
        return DBUtils.getAdapter(DBSObjectContainer.class, containerNode.getObject());
    }

    public DBNDatabaseNode getContainerNode() {
        return containerNode;
    }

    public void setContainerNode(DBNDatabaseNode containerNode) {
        this.containerNode = containerNode;
    }

    public Map<DBSDataContainer, DatabaseMappingContainer> getDataMappings() {
        return dataMappings;
    }

    public DatabaseMappingContainer getDataMapping(DBSDataContainer dataContainer) {
        return dataMappings.get(dataContainer);
    }

    public boolean isCompleted(Collection<DataTransferPipe> pipes) {
        for (DataTransferPipe pipe : pipes) {
            if (pipe.getProducer() != null) {
                DBSDataContainer sourceObject = (DBSDataContainer) pipe.getProducer().getDatabaseObject();
                DatabaseMappingContainer containerMapping = dataMappings.get(sourceObject);
                if (containerMapping == null ||
                    containerMapping.getMappingType() == DatabaseMappingType.unspecified ||
                    !containerMapping.isCompleted()) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isTransferAutoGeneratedColumns() {
        return transferAutoGeneratedColumns;
    }

    public void setTransferAutoGeneratedColumns(boolean transferAutoGeneratedColumns) {
        this.transferAutoGeneratedColumns = transferAutoGeneratedColumns;
    }

    public boolean isTruncateBeforeLoad() {
        return truncateBeforeLoad;
    }

    public void setTruncateBeforeLoad(boolean truncateBeforeLoad) {
        this.truncateBeforeLoad = truncateBeforeLoad;
    }

    public boolean isOpenTableOnFinish() {
        return openTableOnFinish;
    }

    public void setOpenTableOnFinish(boolean openTableOnFinish) {
        this.openTableOnFinish = openTableOnFinish;
    }

    public boolean isOpenNewConnections() {
        return openNewConnections;
    }

    public void setOpenNewConnections(boolean openNewConnections) {
        this.openNewConnections = openNewConnections;
    }

    public boolean isUseTransactions() {
        return useTransactions;
    }

    public void setUseTransactions(boolean useTransactions) {
        this.useTransactions = useTransactions;
    }

    public boolean isUseMultiRowInsert() {
        return useMultiRowInsert;
    }

    public void setUseMultiRowInsert(boolean useMultiRowInsert) {
        this.useMultiRowInsert = useMultiRowInsert;
    }

    public int getMultiRowInsertBatch() {
        return multiRowInsertBatch;
    }

    public void setMultiRowInsertBatch(int multiRowInsertBatch) {
        this.multiRowInsertBatch = multiRowInsertBatch;
    }

    public boolean isDisableUsingBatches() {
        return disableUsingBatches;
    }

    public void setDisableUsingBatches(boolean disableUsingBatches) {
        this.disableUsingBatches = disableUsingBatches;
    }

    public String getOnDuplicateKeyInsertMethodId() {
        return onDuplicateKeyInsertMethodId;
    }

    public void setOnDuplicateKeyInsertMethodId(String onDuplicateKeyInsertMethodId) {
        this.onDuplicateKeyInsertMethodId = onDuplicateKeyInsertMethodId;
    }

    public int getCommitAfterRows() {
        return commitAfterRows;
    }

    public void setCommitAfterRows(int commitAfterRows) {
        this.commitAfterRows = commitAfterRows;
    }

    @Nullable
    public DBPDataSource getTargetDataSource(DatabaseMappingObject attrMapping) {
        DBSObjectContainer container = getContainer();
        if (container != null) {
            return container.getDataSource();
        } else if (attrMapping.getTarget() != null) {
            return attrMapping.getTarget().getDataSource();
        } else {
            return null;
        }
    }

    @Override
    public void loadSettings(DBRRunnableContext runnableContext, DataTransferSettings dataTransferSettings, Map<String, Object> settings) {
        this.dialogSettings = settings;

        containerNodePath = CommonUtils.toString(settings.get("container"), containerNodePath);
        openNewConnections = CommonUtils.getBoolean(settings.get("openNewConnections"), openNewConnections);
        useTransactions = CommonUtils.getBoolean(settings.get("useTransactions"), useTransactions);
        onDuplicateKeyInsertMethodId = CommonUtils.toString(settings.get("onDuplicateKeyMethod"), onDuplicateKeyInsertMethodId);
        commitAfterRows = CommonUtils.toInt(settings.get("commitAfterRows"), commitAfterRows);
        useMultiRowInsert = CommonUtils.getBoolean(settings.get("useMultiRowInsert"), useMultiRowInsert);
        multiRowInsertBatch = CommonUtils.toInt(settings.get("multiRowInsertBatch"), multiRowInsertBatch);
        disableUsingBatches = CommonUtils.getBoolean(settings.get("disableUsingBatches"), disableUsingBatches);
        transferAutoGeneratedColumns = CommonUtils.getBoolean(settings.get("transferAutoGeneratedColumns"), transferAutoGeneratedColumns);
        disableReferentialIntegrity = CommonUtils.getBoolean(settings.get("disableReferentialIntegrity"), disableReferentialIntegrity);
        truncateBeforeLoad = CommonUtils.getBoolean(settings.get("truncateBeforeLoad"), truncateBeforeLoad);
        openTableOnFinish = CommonUtils.getBoolean(settings.get("openTableOnFinish"), openTableOnFinish);

        List<DataTransferPipe> dataPipes = dataTransferSettings.getDataPipes();
        {
            if (!dataPipes.isEmpty()) {
                IDataTransferConsumer consumer = dataPipes.get(0).getConsumer();
                if (consumer instanceof DatabaseTransferConsumer) {
                    final DBSDataManipulator targetObject = ((DatabaseTransferConsumer) consumer).getTargetObject();
                    if (targetObject != null) {
                        containerNode = DBWorkbench.getPlatform().getNavigatorModel().findNode(
                            targetObject.getParentObject()
                        );
                    }
                }
            }
            checkContainerConnection(runnableContext);
        }

        loadNode(runnableContext, dataTransferSettings, null);

        // Load mapping for current objects
        Map<String, Object> mappings = (Map<String, Object>) settings.get("mappings");
        if (mappings != null) {
            if (!dataMappings.isEmpty()) {
                for (DatabaseMappingContainer dmc : dataMappings.values()) {
                    DBSDataContainer sourceDatacontainer = dmc.getSource();
                    if (sourceDatacontainer != null) {
                        Map<String, Object> dmcSettings = (Map<String, Object>) mappings.get(DBUtils.getObjectFullId(sourceDatacontainer));
                        if (dmcSettings != null) {
                            dmc.loadSettings(runnableContext, dmcSettings);
                        }
                    }
                }
            } else if (!dataPipes.isEmpty()) {
                for (DataTransferPipe pipe : dataPipes) {
                    IDataTransferProducer producer = pipe.getProducer();
                    if (producer != null) {
                        DBSObject dbObject = producer.getDatabaseObject();
                        if (dbObject instanceof DBSDataContainer) {
                            DBSDataContainer sourceDC = (DBSDataContainer) dbObject;
                            Map<String, Object> dmcSettings = (Map<String, Object>) mappings.get(DBUtils.getObjectFullId(dbObject));
                            if (dmcSettings != null) {
                                DatabaseMappingContainer dmc = new DatabaseMappingContainer(this, sourceDC);
                                dmc.loadSettings(runnableContext, dmcSettings);
                                dataMappings.put(sourceDC, dmc);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void saveSettings(Map<String, Object> settings) {
        if (containerNode != null) {
            settings.put("container", containerNode.getNodeItemPath());
        }
        settings.put("openNewConnections", openNewConnections);
        settings.put("useTransactions", useTransactions);
        settings.put("commitAfterRows", commitAfterRows);
        settings.put("useMultiRowInsert", useMultiRowInsert);
        settings.put("multiRowInsertBatch", multiRowInsertBatch);
        settings.put("disableUsingBatches", disableUsingBatches);
        settings.put("onDuplicateKeyMethod", onDuplicateKeyInsertMethodId);
        settings.put("transferAutoGeneratedColumns", transferAutoGeneratedColumns);
        settings.put("disableReferentialIntegrity", disableReferentialIntegrity);
        settings.put("truncateBeforeLoad", truncateBeforeLoad);
        settings.put("openTableOnFinish", openTableOnFinish);

        // Load all data mappings
        Map<String, Object> mappings = new LinkedHashMap<>();
        settings.put("mappings", mappings);

        for (DatabaseMappingContainer dmc : dataMappings.values()) {
            DBSDataContainer sourceDatacontainer = dmc.getSource();
            if (sourceDatacontainer != null) {
                Map<String, Object> dmcSettings = new LinkedHashMap<>();
                mappings.put(DBUtils.getObjectFullId(sourceDatacontainer), dmcSettings);
                dmc.saveSettings(dmcSettings);
            }
        }
    }

    @Override
    public String getSettingsSummary() {
        StringBuilder summary = new StringBuilder();

        DTUtils.addSummary(summary, DTMessages.data_transfer_wizard_output_checkbox_new_connection, openNewConnections);
        DTUtils.addSummary(summary, DTMessages.database_consumer_settings_option_use_transactions, useTransactions);
        if (useTransactions) {
            DTUtils.addSummary(summary, DTMessages.database_consumer_settings_option_commit_after, commitAfterRows);
        }
        DTUtils.addSummary(summary, DTMessages.database_consumer_settings_option_use_multi_insert, useMultiRowInsert);
        DTUtils.addSummary(summary, DTMessages.database_consumer_settings_option_multi_insert_batch, multiRowInsertBatch);
        DTUtils.addSummary(summary, DTMessages.database_consumer_settings_option_disable_batches, disableUsingBatches);
        DTUtils.addSummary(summary, DTMessages.database_consumer_settings_option_on_duplicate_key_method_label, onDuplicateKeyInsertMethodId);
        DTUtils.addSummary(summary, DTMessages.database_consumer_settings_option_transfer_auto_generated_columns, transferAutoGeneratedColumns);
        DTUtils.addSummary(summary, DTMessages.database_consumer_settings_option_disable_referential_integrity, disableReferentialIntegrity);
        DTUtils.addSummary(summary, DTMessages.database_consumer_settings_option_truncate_before_load, truncateBeforeLoad);

        return summary.toString();
    }

    private void checkContainerConnection(DBRRunnableContext runnableContext) {
        // If container node is datasource (this may happen if datasource do not support schemas/catalogs)
        // then we need to check connection
        if (containerNode instanceof DBNDataSource && containerNode.getDataSource() == null) {
            try {
                runnableContext.run(true, true,
                    monitor -> {
                        try {
                            containerNode.initializeNode(monitor, null);
                        } catch (DBException e) {
                            throw new InvocationTargetException(e);
                        }
                    });
            } catch (InvocationTargetException e) {
                DBWorkbench.getPlatformUI().showError(DTMessages.database_consumer_settings_title_init_connection,
                        DTMessages.database_consumer_settings_message_error_connecting, e.getTargetException());
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    public void loadNode(DBRRunnableContext runnableContext, DataTransferSettings settings, @Nullable DBSObjectContainer producerContainer) {
        if (containerNode == null && (!CommonUtils.isEmpty(containerNodePath) || producerContainer != null)) {
            if (!CommonUtils.isEmpty(containerNodePath) || producerContainer != null) {
                try {
                    runnableContext.run(true, true, monitor -> {
                        try {
                            DBNNode node;
                            if (!CommonUtils.isEmpty(containerNodePath)) {
                                node = DBWorkbench.getPlatform().getNavigatorModel().getNodeByPath(
                                    monitor,
                                    containerNodePath);
                            } else {
                                node = DBWorkbench.getPlatform().getNavigatorModel().getNodeByObject(producerContainer);
                            }
                            if (node instanceof DBNDatabaseNode) {
                                containerNode = (DBNDatabaseNode) node;
                            }
                        } catch (DBException e) {
                            throw new InvocationTargetException(e);
                        }
                    });
                    checkContainerConnection(runnableContext);
                } catch (InvocationTargetException e) {
                    settings.getState().addError(e.getTargetException());
                    log.error("Error getting container node", e.getTargetException());
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }

    public void addDataMappings(DBRRunnableContext context, DBSDataContainer dataContainer, DatabaseMappingContainer mappingContainer) {
        dataMappings.put(dataContainer, mappingContainer);

        if (mappingContainer.getTarget() == null && dialogSettings != null) {
            // Load settings
            Map<String, Object> mappings = (Map<String, Object>) dialogSettings.get("mappings");
            if (mappings != null) {
                Map<String, Object> dmcSettings = (Map<String, Object>) mappings.get(DBUtils.getObjectFullId(dataContainer));
                if (dmcSettings != null) {
                    mappingContainer.loadSettings(context, dmcSettings);
                }
            }
        }
    }

    public boolean isDisableReferentialIntegrity() {
        return disableReferentialIntegrity;
    }

    public void setDisableReferentialIntegrity(boolean disableReferentialIntegrity) {
        this.disableReferentialIntegrity = disableReferentialIntegrity;
    }
}
