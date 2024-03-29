/*
 * Copyright 2015 herd contributors
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
package org.finra.herd.dao.impl;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;

import javax.persistence.PersistenceException;
import javax.persistence.Tuple;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.sql.DataSource;

import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import org.finra.herd.dao.StorageFileDao;
import org.finra.herd.dao.helper.HerdDaoSecurityHelper;
import org.finra.herd.model.dto.ConfigurationValue;
import org.finra.herd.model.jpa.StorageEntity;
import org.finra.herd.model.jpa.StorageEntity_;
import org.finra.herd.model.jpa.StorageFileEntity;
import org.finra.herd.model.jpa.StorageFileEntity_;
import org.finra.herd.model.jpa.StorageUnitEntity;
import org.finra.herd.model.jpa.StorageUnitEntity_;

@SuppressFBWarnings(value = "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING",
    justification = "The SQL used within this class is provided as a performance enhancement.")
@Repository
public class StorageFileDaoImpl extends AbstractHerdDao implements StorageFileDao
{
    private static final Logger LOGGER = LoggerFactory.getLogger(StorageFileDaoImpl.class);

    @Autowired
    HerdDaoSecurityHelper herdDaoSecurityHelper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Override
    public StorageFileEntity getStorageFileByStorageNameAndFilePath(String storageName, String filePath)
    {
        // Create the criteria builder and the criteria.
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<StorageFileEntity> criteria = builder.createQuery(StorageFileEntity.class);

        // The criteria root is the storage files.
        Root<StorageFileEntity> storageFileEntity = criteria.from(StorageFileEntity.class);

        // Join to the other tables we can filter on.
        Join<StorageFileEntity, StorageUnitEntity> storageUnitEntity = storageFileEntity.join(StorageFileEntity_.storageUnit);
        Join<StorageUnitEntity, StorageEntity> storageEntity = storageUnitEntity.join(StorageUnitEntity_.storage);

        // Create the standard restrictions (i.e. the standard where clauses).
        Predicate filePathRestriction = builder.equal(storageFileEntity.get(StorageFileEntity_.path), filePath);
        Predicate storageNameRestriction = builder.equal(builder.upper(storageEntity.get(StorageEntity_.name)), storageName.toUpperCase());

        criteria.select(storageFileEntity).where(builder.and(filePathRestriction, storageNameRestriction));

        return executeSingleResultQuery(criteria,
            String.format("Found more than one storage file with parameters {storageName=\"%s\"," + " filePath=\"%s\"}.", storageName, filePath));
    }

    @Override
    public StorageFileEntity getStorageFileByStorageUnitEntityAndFilePath(StorageUnitEntity storageUnitEntity, String filePath)
    {
        // Create the criteria builder and the criteria.
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<StorageFileEntity> criteria = builder.createQuery(StorageFileEntity.class);

        // The criteria root is the storage files.
        Root<StorageFileEntity> storageFileEntity = criteria.from(StorageFileEntity.class);

        // Create the standard restrictions (i.e. the standard where clauses).
        Predicate filePathRestriction = builder.equal(storageFileEntity.get(StorageFileEntity_.path), filePath);
        Predicate storageUnitRestriction = builder.equal(storageFileEntity.get(StorageFileEntity_.storageUnitId), storageUnitEntity.getId());

        criteria.select(storageFileEntity).where(builder.and(filePathRestriction, storageUnitRestriction));

        return executeSingleResultQuery(criteria, String
            .format("Found more than one storage file with parameters {storageUnitId=\"%s\"," + " filePath=\"%s\"}.", storageUnitEntity.getId(), filePath));
    }

    @Override
    public MultiValuedMap<Long, String> getStorageFilePathsByStorageUnitIds(List<Long> storageUnitIds)
    {
        // Create a map that can hold a collection of values against each key.
        MultiValuedMap<Long, String> result = new ArrayListValuedHashMap<>();

        // Retrieve the chunk size configured in the system to use when listing storage unit ids in the "in" clause.
        Integer inClauseChunkSize = configurationHelper.getProperty(ConfigurationValue.STORAGE_FILE_PATHS_QUERY_IN_CLAUSE_CHUNK_SIZE, Integer.class);

        // Retrieve the pagination size for the storage file paths query configured in the system.
        Integer paginationSize = configurationHelper.getProperty(ConfigurationValue.STORAGE_FILE_PATHS_QUERY_PAGINATION_SIZE, Integer.class);

        // Create the criteria builder and the criteria.
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> criteria = builder.createTupleQuery();

        // The criteria root is the storage file.
        Root<StorageFileEntity> storageFileEntity = criteria.from(StorageFileEntity.class);

        // Get the columns.
        Path<Long> storageUnitIdColumn = storageFileEntity.get(StorageFileEntity_.storageUnitId);
        Path<String> storageFilePathColumn = storageFileEntity.get(StorageFileEntity_.path);

        // Add the select clause.
        criteria.multiselect(storageUnitIdColumn, storageFilePathColumn);

        // Add the orderBy to the query so we can get consistent pagination results.
        // To force database to use alternate key index in the query execution plan, we order query results by columns from the alternate key index.
        criteria.orderBy(Lists.newArrayList(builder.asc(storageUnitIdColumn), builder.asc(storageFilePathColumn)));

        // Get size of the storage unit list.
        int listSize = CollectionUtils.size(storageUnitIds);

        // Treat zero and negatives as meaning no need to split list of storage unit ids into chunks.
        if (inClauseChunkSize <= 0)
        {
            inClauseChunkSize = listSize;
        }

        // Loop through each chunk of storage unit ids until we have reached the end of the list.
        for (int i = 0; i < listSize; i += inClauseChunkSize)
        {
            // Get a sub-list for the current chunk of data.
            List<Long> storageUnitIdsSubList = storageUnitIds.subList(i, (listSize > (i + inClauseChunkSize) ? (i + inClauseChunkSize) : listSize));

            // Add the where clause for the sub list.
            criteria.where(getPredicateForInClause(builder, storageUnitIdColumn, storageUnitIdsSubList));

            // Execute the query using pagination and populate the result map.
            int startPosition = 0;
            while (true)
            {
                // Run the query to get a list of tuples back.
                List<Tuple> tuples = entityManager.createQuery(criteria).setFirstResult(startPosition).setMaxResults(paginationSize).getResultList();

                // Populate the result map from the returned tuples (i.e. 1 tuple for each row).
                for (Tuple tuple : tuples)
                {
                    // Extract the tuple values.
                    Long storageUnitId = tuple.get(storageUnitIdColumn);
                    String storageFilePath = tuple.get(storageFilePathColumn);

                    // Update the result map.
                    result.put(storageUnitId, storageFilePath);
                }

                // Break out of the while loop if we got less results than the pagination size.
                if (tuples.size() < paginationSize)
                {
                    break;
                }

                // Increment the start position.
                startPosition += paginationSize;
            }
        }

        return result;
    }

    @Override
    public void saveStorageFiles(final List<StorageFileEntity> storageFileEntities)
    {
        // Get the current user id.
        String currentUserId = herdDaoSecurityHelper.getCurrentUsername();

        // Create the insert into storage file table sql.
        final String INSERT_INTO_STORAGE_FILE_TABLE_SQL =
            "INSERT INTO strge_file " + "(strge_file_id, fully_qlfd_file_nm, file_size_in_bytes_nb, row_ct, strge_unit_id, creat_ts, creat_user_id) " +
                "VALUES (nextval('strge_file_seq'), ?, ?, ?, ?, current_timestamp, '" + currentUserId + "')";

        // Obtain the datasource.
        final DataSource dataSource = jdbcTemplate.getDataSource();

        // Both Connection and PreparedStatement classes extend AutoCloseable so use try with resources.
        try (final Connection connection = dataSource.getConnection();
             final PreparedStatement preparedStatement = connection.prepareStatement(INSERT_INTO_STORAGE_FILE_TABLE_SQL))
        {
            // Set auto commit to false to perform a batch insert.
            connection.setAutoCommit(false);

            // Retrieve the JDBC batch size configured in the system.
            final int batchSize = configurationHelper.getProperty(ConfigurationValue.JDBC_BATCH_SIZE, Integer.class);

            // Keep a count of prepared statements.
            int preparedStatementCount = 0;

            // For each storage file entry add to a prepared statement batch and execute.
            for (final StorageFileEntity storageFileEntity : storageFileEntities)
            {
                // Set the path value in the prepared statement as parameter 1.
                if (storageFileEntity.getPath() == null)
                {
                    preparedStatement.setNull(1, Types.VARCHAR);
                }
                else
                {
                    preparedStatement.setString(1, storageFileEntity.getPath());
                }

                // Set the file size bytes in the prepared statement as parameter 2.
                if (storageFileEntity.getFileSizeBytes() == null)
                {
                    preparedStatement.setNull(2, Types.BIGINT);
                }
                else
                {
                    preparedStatement.setLong(2, storageFileEntity.getFileSizeBytes());
                }

                // Set the row count in the prepared statement as parameter 3.
                if (storageFileEntity.getRowCount() == null)
                {
                    preparedStatement.setNull(3, Types.BIGINT);
                }
                else
                {
                    preparedStatement.setLong(3, storageFileEntity.getRowCount());
                }

                preparedStatement.setLong(4, storageFileEntity.getStorageUnit().getId());
                preparedStatement.addBatch();

                LOGGER.debug("Preparing to execute statement: " + preparedStatement.toString());

                // Increase the count of prepared statements added to the batch.
                preparedStatementCount++;

                // If the prepared statement count is a modulo of the batch size or if the prepared statement count reaches the size of the storageFileEntities
                // then execute the batch of prepared statements.
                if (preparedStatementCount % batchSize == 0 || preparedStatementCount == storageFileEntities.size())
                {
                    LOGGER.info("Executing batch of batchSize={}", batchSize);
                    int[] updateCounts = preparedStatement.executeBatch();
                    preparedStatement.clearBatch();
                    LOGGER.info("Batch update complete updateCounts={}", Arrays.toString(updateCounts));
                }
            }

            // Commit the updates.
            connection.commit();
        }
        catch (final BatchUpdateException batchUpdateException)
        {
            LOGGER
                .error("Caught batch update exception. SQLState=\"{}\", Message=\"{}\", ErrorCode=\"{}\", updateCounts={}", batchUpdateException.getSQLState(),
                    batchUpdateException.getMessage(), batchUpdateException.getErrorCode(), Arrays.toString(batchUpdateException.getUpdateCounts()));
            throw new PersistenceException(batchUpdateException);
        }
        catch (final SQLException sqlException)
        {
            LOGGER.error("Caught SQL exception. SQLState=\"{}\", Message=\"{}\", ErrorCode=\"{}\"", sqlException.getSQLState(), sqlException.getMessage(),
                sqlException.getErrorCode());
            throw new PersistenceException(sqlException);
        }
    }
}
