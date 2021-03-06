/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.replication.primaryreplica.route.engine;

import org.apache.shardingsphere.infra.config.properties.ConfigurationProperties;
import org.apache.shardingsphere.infra.database.DefaultSchema;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.infra.route.context.RouteMapper;
import org.apache.shardingsphere.infra.route.context.RouteUnit;
import org.apache.shardingsphere.infra.route.decorator.RouteDecorator;
import org.apache.shardingsphere.replication.primaryreplica.constant.PrimaryReplicaReplicationOrder;
import org.apache.shardingsphere.replication.primaryreplica.route.engine.impl.PrimaryReplicaReplicationDataSourceRouter;
import org.apache.shardingsphere.replication.primaryreplica.rule.PrimaryReplicaReplicationDataSourceRule;
import org.apache.shardingsphere.replication.primaryreplica.rule.PrimaryReplicaReplicationRule;
import org.apache.shardingsphere.sql.parser.binder.statement.SQLStatementContext;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Route decorator for primary-replica replication.
 */
public final class PrimaryReplicaReplicationRouteDecorator implements RouteDecorator<PrimaryReplicaReplicationRule> {
    
    @Override
    public void decorate(final RouteContext routeContext, final SQLStatementContext<?> sqlStatementContext, final List<Object> parameters,
                         final ShardingSphereMetaData metaData, final PrimaryReplicaReplicationRule rule, final ConfigurationProperties props) {
        if (routeContext.getRouteUnits().isEmpty()) {
            firstDecorate(routeContext, sqlStatementContext, rule);
        } else {
            continueDecorate(routeContext, sqlStatementContext, rule);
        }
    }
    
    private void firstDecorate(final RouteContext routeContext, final SQLStatementContext<?> sqlStatementContext, final PrimaryReplicaReplicationRule rule) {
        String dataSourceName = new PrimaryReplicaReplicationDataSourceRouter(rule.getSingleDataSourceRule()).route(sqlStatementContext.getSqlStatement());
        routeContext.getRouteUnits().add(new RouteUnit(new RouteMapper(DefaultSchema.LOGIC_NAME, dataSourceName), Collections.emptyList()));
    }
    
    private void continueDecorate(final RouteContext routeContext, final SQLStatementContext<?> sqlStatementContext, final PrimaryReplicaReplicationRule rule) {
        Collection<RouteUnit> toBeRemoved = new LinkedList<>();
        Collection<RouteUnit> toBeAdded = new LinkedList<>();
        for (RouteUnit each : routeContext.getRouteUnits()) {
            String dataSourceName = each.getDataSourceMapper().getLogicName();
            Optional<PrimaryReplicaReplicationDataSourceRule> dataSourceRule = rule.findDataSourceRule(dataSourceName);
            if (dataSourceRule.isPresent() && dataSourceRule.get().getName().equalsIgnoreCase(each.getDataSourceMapper().getActualName())) {
                toBeRemoved.add(each);
                String actualDataSourceName = new PrimaryReplicaReplicationDataSourceRouter(dataSourceRule.get()).route(sqlStatementContext.getSqlStatement());
                toBeAdded.add(new RouteUnit(new RouteMapper(each.getDataSourceMapper().getLogicName(), actualDataSourceName), each.getTableMappers()));
            }
        }
        routeContext.getRouteUnits().removeAll(toBeRemoved);
        routeContext.getRouteUnits().addAll(toBeAdded);
    }
    
    @Override
    public int getOrder() {
        return PrimaryReplicaReplicationOrder.ORDER;
    }
    
    @Override
    public Class<PrimaryReplicaReplicationRule> getTypeClass() {
        return PrimaryReplicaReplicationRule.class;
    }
}
