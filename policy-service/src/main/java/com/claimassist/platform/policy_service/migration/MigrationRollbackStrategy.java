package com.claimassist.platform.policy_service.migration;

/**
 * Migration Rollback Strategy
 *
 * This document defines the rollback strategy for policy migration execution.
 *
 * TRANSACTION BOUNDARIES:
 * - Each policy migration is transactional (Policy + PolicyVersion + Reconciliation)
 * - @Transactional on migrateSinglePolicy() ensures atomicity
 * - If any part fails, the entire transaction rolls back
 * - No partial state is committed
 *
 * FAILURE SCENARIOS:
 *
 * 1. Validation Failure (Dry-Run)
 *    - Before any database writes
 *    - Record classified as SKIPPED or FAILED
 *    - No transaction started
 *    - No rollback needed
 *
 * 2. Already Migrated
 *    - Reconciliation lookup finds existing mapping
 *    - Policy number matches (no source change)
 *    - Record classified as ALREADY_MIGRATED
 *    - No rollback needed
 *
 * 3. Source Changed
 *    - Reconciliation lookup finds existing mapping
 *    - Policy number differs (source data changed)
 *    - Record classified as SKIPPED with SOURCE_CHANGED
 *    - No rollback needed (existing data preserved)
 *    - Manual review required
 *
 * 4. Policy Creation Failure
 *    - Policy save fails (validation, constraint, etc.)
 *    - Transaction rolls back automatically
 *    - No PolicyVersion created
 *    - No reconciliation mapping created
 *    - Next retry can attempt again
 *
 * 5. PolicyVersion Creation Failure
 *    - Policy created successfully
 *    - PolicyVersion save fails
 *    - Transaction rolls back automatically
 *    - Policy is rolled back
 *    - No reconciliation mapping created
 *    - Next retry can attempt again
 *
 * 6. Reconciliation Mapping Failure
 *    - Policy and PolicyVersion created successfully
 *    - Reconciliation mapping save fails (constraint, etc.)
 *    - Transaction rolls back automatically
 *    - Policy and PolicyVersion are rolled back
 *    - Next retry can attempt again
 *
 * DUPLICATE/CONFLICT HANDLING:
 *
 * 1. Legacy ID Conflict
 *    - Reconciliation lookup finds same legacy ID mapped to different target
 *    - Dry-run validation detects this
 *    - Record classified as SKIPPED with LEGACY_ID_CONFLICT
 *    - No rollback needed (migration not attempted)
 *
 * 2. Target ID Conflict
 *    - Reconciliation lookup finds target ID mapped to different legacy
 *    - Dry-run validation detects this
 *    - Record classified as SKIPPED with RECONCILIATION_CONFLICT
 *    - No rollback needed (migration not attempted)
 *
 * 3. Policy Number Conflict
 *    - Reconciliation lookup finds policy number mapped to different target
 *    - Dry-run validation detects this
 *    - Record classified as SKIPPED with POLICY_NUMBER_CONFLICT
 *    - No rollback needed (migration not attempted)
 *
 * RETRY BEHAVIOR:
 *
 * - Failed records can be retried after fixing the issue
 * - Validation failures: fix data or configuration, retry
 * - Transaction failures: fix constraint/validation, retry
 * - Source changed: manual review before retry
 * - Conflicts: manual resolution before retry
 *
 * - Re-run of entire dataset:
 *   - Already migrated records: skipped (ALREADY_MIGRATED)
 *   - Failed records: re-attempted
 *   - New records: attempted
 *
 * PARTIAL AGGREGATE HANDLING:
 *
 * - Dataset migration processes records one by one
 * - Each record has its own transaction
 * - If record N fails, records 1..N-1 remain committed
 * - Record N+1..end are not attempted until retry
 * - This ensures maximum progress while maintaining per-record atomicity
 *
 * WHAT HAPPENS TO SUCCESSFULLY MIGRATED RECORDS IF LATER RECORD FAILS:
 *
 * - Successfully migrated records (1..N-1) remain in the database
 * - They are not rolled back when record N fails
 * - Failed record N is marked as FAILED in the execution result
 * - Remaining records (N+1..end) are not processed
 * - Re-running the dataset will:
 *   - Skip records 1..N-1 (ALREADY_MIGRATED)
 *   - Re-attempt record N
 *   - Process remaining records
 *
 * This design ensures:
 * - No data loss (successful migrations persist)
 * - Idempotency (re-runs are safe)
 * - Progress visibility (execution result shows status of each record)
 * - Recovery capability (failed records can be retried)
 *
 * MANUAL ROLLBACK (if needed):
 *
 * If a complete rollback of a migration is required:
 *
 * 1. Identify all records in the migration batch using:
 *    - execution result
 *    - reconciliation table
 *    - timestamp/batchId tracking
 *
 * 2. Delete in reverse order of creation:
 *    - Delete from legacy_customer_policy_id_map
 *    - Delete from policy_version
 *    - Delete from policies
 *
 * 3. Execute in a single transaction for atomicity
 *
 * NOTE: Manual rollback is an emergency procedure and should not be needed
 * under normal circumstances due to the per-record transaction design.
 *
 * ROLLBACK SAFETY:
 *
 * - No destructive rollback against OCI (per safety rules)
 * - Rollback only applies to local/test databases
 * - Production rollback requires explicit authorization
 * - Rollback procedures must be tested in staging first
 */
public class MigrationRollbackStrategy {
    // This is a documentation class only - no implementation required
}
