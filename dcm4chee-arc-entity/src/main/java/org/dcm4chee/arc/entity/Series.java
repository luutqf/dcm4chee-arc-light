/*
 * *** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.entity;

import jakarta.persistence.*;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.DateUtils;
import org.dcm4che3.util.Property;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.conf.Duration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Stream;

/**
 * @author Damien Evans <damien.daddy@gmail.com>
 * @author Justin Falk <jfalkmu@gmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 */
@NamedQueries({
@NamedQuery(
    name=Series.FIND_BY_SERIES_IUID,
    query="select se from Series se " +
            "join se.study st " +
            "where st.studyInstanceUID = ?1 " +
            "and se.seriesInstanceUID = ?2 "),
@NamedQuery(
    name=Series.ATTRS_BY_SERIES_IUID,
    query="select a.encodedAttributes from Series se " +
            "join se.study st " +
            "join se.attributesBlob a " +
            "where st.studyInstanceUID = ?1 " +
            "and se.seriesInstanceUID = ?2 "),
@NamedQuery(
        name=Series.FIND_SERIES_OF_STUDY_BY_STUDY_IUID_EAGER,
        query="select se from Series se " +
                "join fetch se.study st " +
                "join fetch se.attributesBlob " +
                "join fetch st.attributesBlob " +
                "where st.studyInstanceUID = ?1 "),
@NamedQuery(
    name=Series.FIND_BY_SERIES_IUID_EAGER,
    query="select se from Series se " +
            "join fetch se.study st " +
            "join fetch st.patient p " +
            "left join fetch p.patientName " +
            "left join fetch st.referringPhysicianName " +
            "left join fetch se.performingPhysicianName " +
            "join fetch se.attributesBlob " +
            "join fetch st.attributesBlob " +
            "join fetch p.attributesBlob " +
            "where st.studyInstanceUID = ?1 " +
            "and se.seriesInstanceUID = ?2"),
@NamedQuery(
    name = Series.SERIES_PKS_OF_STUDY_WITH_UNKNOWN_SIZE,
    query = "select se.pk from Series se " +
            "where se.study.pk = ?1 and se.size = -1"),
@NamedQuery(
    name = Series.SERIES_PKS_OF_STUDY_BY_STUDY_IUID,
    query = "select se.pk from Series se " +
            "where se.study.studyInstanceUID = ?1"),
@NamedQuery(name = Series.SIZE_OF_STUDY,
    query = "select sum(se.size) from Series se " +
            "where se.study.pk = ?1 and se.size != -1"),
@NamedQuery(
    name=Series.SET_SERIES_SIZE,
    query="update Series se set se.size = ?2 where se.pk = ?1"),
@NamedQuery(
    name=Series.SET_EXTERNAL_RETRIEVE_AET_BY_SERIES_PK,
    query="update Series se set se.externalRetrieveAET = ?2 " +
            "where se.pk = ?1 and se.externalRetrieveAET != ?2"),
@NamedQuery(
    name=Series.SET_EXTERNAL_RETRIEVE_AET_BY_SERIES_IUID,
    query="update Series se set se.externalRetrieveAET = ?2 " +
            "where se.seriesInstanceUID = ?1 and se.externalRetrieveAET != ?2"),
@NamedQuery(
    name=Series.DISTINCT_EXTERNAL_RETRIEVE_AET_BY_STUDY_IUID,
    query = "select distinct se.externalRetrieveAET from Series se " +
            "where se.study.studyInstanceUID = ?1"),
@NamedQuery(
    name=Series.RESET_SERIES_SIZE_AND_EXTERNAL_RETRIEVE_AET,
    query="update Series se set se.size = -1L, se.externalRetrieveAET = ?2 where se.pk = ?1"),
@NamedQuery(
    name=Series.SET_COMPLETENESS,
    query="update Series ser set ser.completeness = ?3 " +
            "where ser.seriesInstanceUID = ?2 and ser.completeness != ?3 and ser.study in (" +
            "select study from Study study where study.studyInstanceUID = ?1)"),
@NamedQuery(
    name=Series.SET_COMPLETENESS_OF_STUDY,
    query="update Series ser set ser.completeness = ?2 " +
            "where ser.completeness != ?2 and ser.study in (" +
            "select study from Study study where study.studyInstanceUID = ?1)"),
@NamedQuery(
    name=Series.INCREMENT_FAILED_RETRIEVES,
    query="update Series ser set ser.failedRetrieves = ser.failedRetrieves + 1, ser.completeness = ?3 " +
            "where ser.seriesInstanceUID = ?2 and ser.study in (" +
            "select study from Study study where study.studyInstanceUID = ?1)"),
@NamedQuery(
    name=Series.UPDATE_STGVER_FAILURES,
    query="update Series ser set ser.failuresOfLastStorageVerification = ?2, ser.size = ?3 " +
            "where ser.pk =?1"),
@NamedQuery(
    name=Series.UPDATE_COMPRESSION_FAILURES,
        query="update Series ser set ser.compressionFailures = ?2 where ser.pk = ?1"),
@NamedQuery(
    name=Series.UPDATE_COMPRESSION_FAILURES_AND_TSUID,
    query="update Series ser set ser.compressionFailures = ?2, ser.transferSyntaxUID = ?3 where ser.pk = ?1"),
@NamedQuery(
    name=Series.UPDATE_COMPRESSION_COMPLETED,
    query="update Series ser set ser.compressionFailures = ?2, ser.transferSyntaxUID = ?3, " +
            "ser.compressionTransferSyntaxUID = null, ser.compressionImageWriteParams = null " +
            "where ser.pk = ?1"),
@NamedQuery(
    name=Series.COUNT_SERIES_OF_STUDY,
    query="select count(se) from Series se " +
            "where se.study = ?1"),
@NamedQuery(
        name=Series.GET_EXPIRED_SERIES,
        query="select se from Series se " +
             "where se.expirationDate <= ?1 and se.expirationState in ?2"),
@NamedQuery(
        name=Series.CLAIM_EXPIRED_SERIES,
        query="update Series se set se.expirationState = ?3 " +
                "where se.pk = ?1 and se.expirationState = ?2 and se.expirationState != ?3"),
@NamedQuery(
        name=Series.EXPIRE_SERIES,
        query="update Series se set se.expirationState = ?2 , se.expirationDate = ?3 " +
                "where se.study.pk = ?1"),
@NamedQuery(
        name=Series.FIND_SERIES_OF_STUDY,
        query = "select se from Series se " +
                "where se.study.studyInstanceUID = ?1"),
@NamedQuery(
        name = Series.FIND_SERIES_OF_STUDY_BY_INSTANCE_PURGE_STATE,
        query = "select se from Series se " +
                "join fetch se.metadata " +
                "where se.study.studyInstanceUID = ?1 " +
                "and se.instancePurgeState = ?2"),
@NamedQuery(
        name = Series.FIND_BY_SERIES_IUID_AND_INSTANCE_PURGE_STATE,
        query = "select se from Series se " +
                "join fetch se.metadata " +
                "where se.study.studyInstanceUID = ?1 " +
                "and se.seriesInstanceUID = ?2 " +
                "and se.instancePurgeState = ?3"),
@NamedQuery(
        name = Series.COUNT_BY_STUDY_UID,
        query = "select count(se) from Series se " +
                "where se.study.studyInstanceUID = ?1"),
@NamedQuery(
        name = Series.COUNT_BY_SERIES_UID,
        query = "select count(se) from Series se " +
                "where se.study.studyInstanceUID = ?1 " +
                "and se.seriesInstanceUID = ?2"),
@NamedQuery(
        name=Series.COUNT_SERIES_OF_STUDY_WITH_OTHER_REJECTION_STATE,
        query="select count(se) from Series se " +
                "where se.study = ?1 and se.rejectionState <> ?2"),
@NamedQuery(
        name=Series.SERIES_IUIDS_OF_STUDY,
        query="select se.study.studyInstanceUID, se.seriesInstanceUID from Series se " +
                "where se.study.studyInstanceUID = ?1"),
@NamedQuery(
        name = Series.SCHEDULED_METADATA_UPDATE,
        query = "select new org.dcm4chee.arc.entity.Series$MetadataUpdate(" +
                "se.pk, se.version, se.metadataUpdateLoadObjects, se.metadataUpdateFailures, " +
                "se.instancePurgeState, metadata.storageID, metadata.storagePath) " +
                "from Series se " +
                "left join se.metadata metadata " +
                "where se.metadataScheduledUpdateTime < current_timestamp"),
@NamedQuery(
        name = Series.SCHEDULED_PURGE_INSTANCES,
        query = "select new org.dcm4chee.arc.entity.Series$MetadataUpdate(" +
                "se.pk, se.version, se.metadataUpdateLoadObjects, se.metadataUpdateFailures, " +
                "se.instancePurgeState, metadata.storageID, metadata.storagePath) " +
                "from Series se " +
                "join se.metadata metadata " +
                "where se.instancePurgeTime < current_timestamp " +
                "and se.metadataScheduledUpdateTime is null"),
@NamedQuery(
        name=Series.SCHEDULE_METADATA_UPDATE_FOR_PATIENT,
        query = "update Series se set se.metadataScheduledUpdateTime = current_timestamp " +
                "where se.study in (select st from Study st where st.patient = ?1) " +
                "and se.metadata is not null " +
                "and se.metadataScheduledUpdateTime is null"),
@NamedQuery(
        name=Series.SCHEDULE_METADATA_UPDATE_FOR_STUDY,
        query = "update Series se set se.metadataScheduledUpdateTime = current_timestamp " +
                "where se.study = ?1 " +
                "and se.metadata is not null " +
                "and se.metadataScheduledUpdateTime is null"),
@NamedQuery(
        name=Series.SCHEDULE_METADATA_UPDATE_FOR_SERIES,
        query = "update Series se set se.metadataScheduledUpdateTime = current_timestamp " +
                "where se.pk = ?1 " +
                "and se.metadata is not null " +
                "and se.metadataScheduledUpdateTime is null"),
@NamedQuery(
        name=Series.SCHEDULE_METADATA_UPDATE_FOR_SERIES_UID,
        query = "update Series se set se.metadataScheduledUpdateTime = current_timestamp " +
                "where se.study in (select st from Study st where st.studyInstanceUID = ?1) " +
                "and se.seriesInstanceUID = ?2 " +
                "and se.metadata is not null " +
                "and se.metadataScheduledUpdateTime is null"),
@NamedQuery(
        name = Series.FIND_DISTINCT_MODALITIES,
        query = "select distinct se.modality from Series se"),
@NamedQuery(
        name = Series.FIND_DISTINCT_INSTITUTIONS,
        query = "select distinct se.institutionName from Series se"),
@NamedQuery(
        name=Series.UPDATE_INSTANCE_PURGE_STATE,
        query = "update Series se set se.instancePurgeState = ?3 " +
                "where se.pk = ?1 and se.instancePurgeState = ?2 and se.instancePurgeState != ?3"),
@NamedQuery(
        name = Series.FIND_BY_STUDY_PK_AND_INSTANCE_PURGE_STATE,
        query = "select se from Series se join fetch se.metadata where se.study.pk=?1 and se.instancePurgeState=?2"),
@NamedQuery(
        name = Series.SCHEDULED_STORAGE_VERIFICATION,
        query = "select new org.dcm4chee.arc.entity.Series$StorageVerification(" +
                "se.pk, se.version, se.storageVerificationTime, se.seriesInstanceUID, se.study.studyInstanceUID) " +
                "from Series se " +
                "where se.storageVerificationTime < current_timestamp"),
@NamedQuery(
        name = Series.SCHEDULED_COMPRESSION,
        query = "select new org.dcm4chee.arc.entity.Series$Compression(" +
                "se.study.pk, se.pk, se.version, se.instancePurgeState, se.compressionTransferSyntaxUID, " +
                "se.compressionImageWriteParams, se.seriesInstanceUID, se.study.studyInstanceUID) " +
                "from Series se " +
                "where se.compressionTime < current_timestamp"),
@NamedQuery(
        name=Series.INCREMENT_METADATA_UPDATE_FAILURES,
        query = "update Series se set se.metadataUpdateFailures = se.metadataUpdateFailures + 1, se.metadataScheduledUpdateTime = ?2 " +
                "where se.pk = ?1"),
@NamedQuery(
        name=Series.SET_METADATA,
        query = "update Series se set se.metadata = ?2, se.metadataUpdateFailures = 0, se.metadataScheduledUpdateTime = null, se.metadataUpdateLoadObjects = false " +
                "where se.pk = ?1"),
@NamedQuery(
        name=Series.CLAIM_STORAGE_VERIFICATION,
        query = "update Series se set se.storageVerificationTime = ?3, se.version = se.version + 1 " +
                "where se.pk = ?1 and se.version = ?2"),
@NamedQuery(
        name=Series.CLAIM_COMPRESSION,
        query = "update Series se set se.compressionTime = null, se.version = se.version + 1 " +
                "where se.pk = ?1 and se.version =?2"),
@NamedQuery(
        name=Series.CLAIM_UPDATE_METADATA,
        query = "update Series se set se.metadataScheduledUpdateTime = null, se.version = se.version + 1 " +
                "where se.pk = ?1 and se.version = ?2"),
@NamedQuery(
        name=Series.CLAIM_PURGE_INSTANCE_RECORDS,
        query = "update Series se set se.instancePurgeTime = null, se.version = se.version + 1 " +
                "where se.pk = ?1 and se.version = ?2"),
@NamedQuery(
        name=Series.FIND_LAST_MODIFIED_STUDY_LEVEL,
        query="SELECT p.updatedTime, st.modifiedTime, MAX(se.modifiedTime) from Series se " +
                "JOIN se.study st " +
                "JOIN st.patient p " +
                "where st.studyInstanceUID = ?1 " +
                "and se.instancePurgeState = ?2 " +
                "GROUP BY p, st" ),
@NamedQuery(
        name = Series.UPDATE_ACCESS_CONTROL_ID,
        query = "update Series se set se.accessControlID = ?3 " +
                "where se.seriesInstanceUID = ?2 and se.accessControlID != ?3 and se.study in (" +
                "select study from Study study where study.studyInstanceUID = ?1)"),
@NamedQuery(
        name=Series.FIND_LAST_MODIFIED_SERIES_LEVEL,
        query="SELECT p.updatedTime, st.modifiedTime, se.modifiedTime from Series se " +
                "JOIN se.study st " +
                "JOIN st.patient p " +
                "where st.studyInstanceUID = ?1 " +
                "and se.seriesInstanceUID = ?2 " +
                "and se.instancePurgeState = ?3")
})
@Entity
@Table(name = "series",
    uniqueConstraints = @UniqueConstraint(columnNames = { "study_fk", "series_iuid" }),
    indexes = {
        @Index(columnList = "created_time"),
        @Index(columnList = "series_iuid"),
        @Index(columnList = "access_control_id"),
        @Index(columnList = "rejection_state"),
        @Index(columnList = "series_no"),
        @Index(columnList = "modality"),
        @Index(columnList = "sop_cuid"),
        @Index(columnList = "tsuid"),
        @Index(columnList = "station_name"),
        @Index(columnList = "pps_start_date"),
        @Index(columnList = "pps_start_time"),
        @Index(columnList = "body_part"),
        @Index(columnList = "laterality"),
        @Index(columnList = "series_desc"),
        @Index(columnList = "institution"),
        @Index(columnList = "department"),
        @Index(columnList = "series_custom1"),
        @Index(columnList = "series_custom2"),
        @Index(columnList = "series_custom3"),
        @Index(columnList = "sending_aet"),
        @Index(columnList = "receiving_aet"),
        @Index(columnList = "sending_pres_addr"),
        @Index(columnList = "receiving_pres_addr"),
        @Index(columnList = "sending_hl7_app"),
        @Index(columnList = "sending_hl7_facility"),
        @Index(columnList = "receiving_hl7_app"),
        @Index(columnList = "receiving_hl7_facility"),
        @Index(columnList = "expiration_state"),
        @Index(columnList = "expiration_date"),
        @Index(columnList = "failed_retrieves"),
        @Index(columnList = "completeness"),
        @Index(columnList = "metadata_update_time"),
        @Index(columnList = "metadata_update_failures"),
        @Index(columnList = "inst_purge_time"),
        @Index(columnList = "inst_purge_state"),
        @Index(columnList = "stgver_time"),
        @Index(columnList = "stgver_failures"),
        @Index(columnList = "compress_time"),
        @Index(columnList = "compress_failures")
})
public class Series {

    public static final String FIND_BY_SERIES_IUID = "Series.findBySeriesIUID";
    public static final String ATTRS_BY_SERIES_IUID = "Series.attrsBySeriesIUID";
    public static final String FIND_SERIES_OF_STUDY_BY_STUDY_IUID_EAGER = "Series.findSeriesOfStudyByStudyIUIDEager";
    public static final String FIND_BY_SERIES_IUID_EAGER = "Series.findBySeriesIUIDEager";
    public static final String COUNT_SERIES_OF_STUDY = "Series.countSeriesOfStudy";
    public static final String SERIES_PKS_OF_STUDY_WITH_UNKNOWN_SIZE = "Series.seriesPKsOfStudyWithUnknownSize";
    public static final String SERIES_PKS_OF_STUDY_BY_STUDY_IUID = "Series.seriesPKsOfStudyByStudyPk";
    public static final String SIZE_OF_STUDY="Series.sizeOfStudy";
    public static final String SET_SERIES_SIZE = "Series.SetSeriesSize";
    public static final String SET_EXTERNAL_RETRIEVE_AET_BY_SERIES_PK = "Series.SetExternalRetrieveAETBySeriesPK";
    public static final String SET_EXTERNAL_RETRIEVE_AET_BY_SERIES_IUID = "Series.SetExternalRetrieveAETBySeriesUID";
    public static final String DISTINCT_EXTERNAL_RETRIEVE_AET_BY_STUDY_IUID = "Series.distinctExternalRetrieveAETByStudyUID";
    public static final String RESET_SERIES_SIZE_AND_EXTERNAL_RETRIEVE_AET = "Series.ResetSeriesSizeAndExternalRetrieveAET";
    public static final String SET_COMPLETENESS = "Series.SetCompleteness";
    public static final String SET_COMPLETENESS_OF_STUDY = "Series.SetCompletenessOfStudy";
    public static final String INCREMENT_FAILED_RETRIEVES = "Series.IncrementFailedRetrieves";
    public static final String GET_EXPIRED_SERIES = "Series.GetExpiredSeries";
    public static final String CLAIM_EXPIRED_SERIES = "Series.ClaimExpiredSeries";
    public static final String EXPIRE_SERIES = "Series.ExpireSeries";
    public static final String FIND_SERIES_OF_STUDY = "Series.FindSeriesOfStudy";
    public static final String FIND_SERIES_OF_STUDY_BY_INSTANCE_PURGE_STATE = "Series.FindSeriesOfStudyByInstancePurgeState";
    public static final String FIND_BY_SERIES_IUID_AND_INSTANCE_PURGE_STATE = "Series.FindBySeriesIUIDAndInstancePurgeState";
    public static final String COUNT_BY_STUDY_UID = "Series.CountByStudyUID";
    public static final String COUNT_BY_SERIES_UID = "Series.CountBySeriesUID";
    public static final String COUNT_SERIES_OF_STUDY_WITH_OTHER_REJECTION_STATE = "Series.countSeriesOfStudyWithOtherRejectionState";
    public static final String SERIES_IUIDS_OF_STUDY = "Series.seriesIUIDsOfStudy";
    public static final String SCHEDULED_METADATA_UPDATE = "Series.scheduledMetadataUpdate";
    public static final String SCHEDULED_PURGE_INSTANCES = "Series.scheduledPurgeInstances";
    public static final String SCHEDULE_METADATA_UPDATE_FOR_PATIENT = "Series.scheduleMetadataUpdateForPatient";
    public static final String SCHEDULE_METADATA_UPDATE_FOR_STUDY = "Series.scheduleMetadataUpdateForStudy";
    public static final String SCHEDULE_METADATA_UPDATE_FOR_SERIES = "Series.scheduleMetadataUpdateForSeries";
    public static final String SCHEDULE_METADATA_UPDATE_FOR_SERIES_UID = "Series.scheduleMetadataUpdateForSeriesUID";
    public static final String UPDATE_INSTANCE_PURGE_STATE = "Series.updateInstancePurgeState";
    public static final String FIND_DISTINCT_MODALITIES = "Series.findDistinctModalities";
    public static final String FIND_DISTINCT_INSTITUTIONS = "Series.findDistinctInstitutions";
    public static final String FIND_BY_STUDY_PK_AND_INSTANCE_PURGE_STATE = "Series.findByStudyPkAndInstancePurgeState";
    public static final String UPDATE_STGVER_FAILURES = "Series.updateStgVerFailures";
    public static final String SCHEDULED_STORAGE_VERIFICATION = "Series.scheduledStorageVerification";
    public static final String SCHEDULED_COMPRESSION = "Series.scheduledCompression";
    public static final String INCREMENT_METADATA_UPDATE_FAILURES = "Series.setMetadataScheduledUpdateTime";
    public static final String SET_METADATA = "Series.setMetadata";
    public static final String CLAIM_STORAGE_VERIFICATION = "Series.claimStorageVerification";
    public static final String CLAIM_COMPRESSION = "Series.claimCompression";
    public static final String CLAIM_UPDATE_METADATA = "Series.claimUpdateMetadata";
    public static final String CLAIM_PURGE_INSTANCE_RECORDS = "Series.claimPurgeInstanceRecords";
    public static final String UPDATE_COMPRESSION_FAILURES = "Series.updateCompressionFailures";
    public static final String UPDATE_COMPRESSION_FAILURES_AND_TSUID = "Series.updateCompressionFailuresAndTSUID";
    public static final String UPDATE_COMPRESSION_COMPLETED = "Series.updateCompressionCompleted";
    public static final String FIND_LAST_MODIFIED_STUDY_LEVEL = "Series.findLastModifiedStudyLevel";
    public static final String FIND_LAST_MODIFIED_SERIES_LEVEL = "Series.findLastModifiedSeriesLevel";
    public static final String UPDATE_ACCESS_CONTROL_ID = "Series.UpdateAccessControlID";

    public enum InstancePurgeState { NO, PURGED, FAILED_TO_PURGE }

    public static class MetadataUpdate {
        public final Long seriesPk;
        public final Long version;
        public final boolean loadObjects;
        public final int updateFailures;
        public final InstancePurgeState instancePurgeState;
        public final String storageID;
        public final String storagePath;

        public MetadataUpdate(Long seriesPk, Long version, boolean loadObjects, int updateFailures,
                              InstancePurgeState instancePurgeState, String storageID, String storagePath) {
            this.seriesPk = seriesPk;
            this.version = version;
            this.loadObjects = loadObjects;
            this.updateFailures = updateFailures;
            this.instancePurgeState = instancePurgeState;
            this.storageID = storageID;
            this.storagePath = storagePath;
        }

        public String toString() {
            return "MetadataUpdate[seriesPk=" + seriesPk +
                    ", storageID=" + storageID +
                    ", storagePath=" + storagePath +
                    "]";
        }
    }

    public static class StorageVerification {
        public final Long seriesPk;
        public final Long version;
        public final Date storageVerificationTime;
        public final String seriesInstanceUID;
        public final String studyInstanceUID;

        public StorageVerification(Long seriesPk, Long version, Date storageVerificationTime,
                                   String seriesInstanceUID, String studyInstanceUID) {
            this.seriesPk = seriesPk;
            this.version = version;
            this.storageVerificationTime = storageVerificationTime;
            this.seriesInstanceUID = seriesInstanceUID;
            this.studyInstanceUID = studyInstanceUID;
        }

        public String toString() {
            return "StorageVerification[seriesPk=" + seriesPk +
                    ", storageVerificationTime=" + storageVerificationTime +
                    ", studyUID=" + studyInstanceUID +
                    ", seriesUID=" + seriesInstanceUID +
                    "]";
        }
    }

    public static class Compression {
        public final Long studyPk;
        public final Long seriesPk;
        public final Long version;
        public final InstancePurgeState instancePurgeState;
        public final String transferSyntaxUID;
        public final String imageWriteParams;
        public final String seriesInstanceUID;
        public final String studyInstanceUID;

        public Compression(Long studyPk, Long seriesPk, Long version,
                           InstancePurgeState instancePurgeState,
                           String transferSyntaxUID, String imageWriteParams,
                           String seriesInstanceUID, String studyInstanceUID) {
            this.studyPk = studyPk;
            this.seriesPk = seriesPk;
            this.version = version;
            this.instancePurgeState = instancePurgeState;
            this.transferSyntaxUID = transferSyntaxUID;
            this.imageWriteParams = imageWriteParams;
            this.seriesInstanceUID = seriesInstanceUID;
            this.studyInstanceUID = studyInstanceUID;
        }

        public Property[] imageWriteParams() {
            return parseImageWriteParams(this.imageWriteParams);
        }
    }

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Version
    @Column(name = "version")
    private long version;    

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_time", updatable = false)
    private Date createdTime;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_time")
    private Date updatedTime;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "modified_time")
    private Date modifiedTime;

    @Basic(optional = false)
    @Column(name = "completeness")
    private Completeness completeness;

    @Basic(optional = false)
    @Column(name = "failed_retrieves")
    private int failedRetrieves;

    @Basic(optional = false)
    @Column(name = "series_iuid", updatable = false)
    private String seriesInstanceUID;

    @Column(name = "series_no")
    private Integer seriesNumber;

    @Basic(optional = false)
    @Column(name = "series_desc")
    private String seriesDescription;

    @Basic(optional = false)
    @Column(name = "modality")
    private String modality;

    @Basic(optional = false)
    @Column(name = "sop_cuid")
    private String sopClassUID;

    @Basic(optional = false)
    @Column(name = "tsuid")
    private String transferSyntaxUID;

    @Basic(optional = false)
    @Column(name = "department")
    private String institutionalDepartmentName;

    @Basic(optional = false)
    @Column(name = "institution")
    private String institutionName;

    @Basic(optional = false)
    @Column(name = "station_name")
    private String stationName;

    @Basic(optional = false)
    @Column(name = "body_part")
    private String bodyPartExamined;

    @Basic(optional = false)
    @Column(name = "laterality")
    private String laterality;

    @Basic(optional = false)
    @Column(name = "pps_start_date")
    private String performedProcedureStepStartDate;

    @Basic(optional = false)
    @Column(name = "pps_start_time")
    private String performedProcedureStepStartTime;

    @Basic(optional = false)
    @Column(name = "pps_iuid")
    private String performedProcedureStepInstanceUID;

    @Basic(optional = false)
    @Column(name = "pps_cuid")
    private String performedProcedureStepClassUID;

    @Basic(optional = false)
    @Column(name = "series_custom1")
    private String seriesCustomAttribute1;

    @Basic(optional = false)
    @Column(name = "series_custom2")
    private String seriesCustomAttribute2;

    @Basic(optional = false)
    @Column(name = "series_custom3")
    private String seriesCustomAttribute3;

    @Basic(optional = false)
    @Column(name = "access_control_id")
    private String accessControlID = "*";

    @Column(name = "sending_aet")
    private String sendingAET;

    @Column(name = "receiving_aet")
    private String receivingAET;

    @Column(name = "sending_pres_addr")
    private String sendingPresentationAddress;

    @Column(name = "receiving_pres_addr")
    private String receivingPresentationAddress;

    @Column(name = "sending_hl7_app")
    private String sendingHL7Application;

    @Column(name = "sending_hl7_facility")
    private String sendingHL7Facility;

    @Column(name = "receiving_hl7_app")
    private String receivingHL7Application;

    @Column(name = "receiving_hl7_facility")
    private String receivingHL7Facility;

    @Basic(optional = false)
    @Column(name = "ext_retrieve_aet")
    private String externalRetrieveAET = "*";

    @Basic(optional = false)
    @Column(name = "series_size")
    private long size = -1L;

    @Basic(optional = false)
    @Column(name = "rejection_state")
    private RejectionState rejectionState;

    @Basic(optional = false)
    @Column(name = "expiration_state")
    private ExpirationState expirationState;

    @Basic
    @Column(name = "expiration_date")
    private String expirationDate;

    @Basic
    @Column(name = "expiration_exporter_id")
    private String expirationExporterID;

    @Basic
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "metadata_update_time")
    private Date metadataScheduledUpdateTime;

    @Basic(optional = false)
    @Column(name = "metadata_update_load_objects")
    private boolean metadataUpdateLoadObjects;

    @Basic(optional = false)
    @Column(name = "metadata_update_failures")
    private int metadataUpdateFailures;

    @Basic
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "inst_purge_time")
    private Date instancePurgeTime;

    @Basic(optional = false)
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "inst_purge_state")
    private InstancePurgeState instancePurgeState;

    @Basic
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "stgver_time")
    private Date storageVerificationTime;

    @Basic(optional = false)
    @Column(name = "stgver_failures")
    private int failuresOfLastStorageVerification;

    @Basic
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "compress_time")
    private Date compressionTime;

    @Column(name = "compress_tsuid")
    private String compressionTransferSyntaxUID;

    @Column(name = "compress_params")
    private String compressionImageWriteParams;

    @Basic(optional = false)
    @Column(name = "compress_failures")
    private int compressionFailures;

    @OneToOne(cascade=CascadeType.ALL, orphanRemoval = true, optional = false)
    @JoinColumn(name = "dicomattrs_fk")
    private AttributesBlob attributesBlob;

    @OneToOne(cascade=CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "perf_phys_name_fk")
    private PersonName performingPhysicianName;

    @ManyToOne
    @JoinColumn(name = "inst_code_fk")
    private CodeEntity institutionCode;

    @ManyToOne
    @JoinColumn(name = "dept_code_fk")
    private CodeEntity institutionalDepartmentTypeCode;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "series_fk")
    private Collection<SeriesRequestAttributes> requestAttributes;

    @OneToMany(mappedBy = "series", cascade=CascadeType.ALL)
    private Collection<SeriesQueryAttributes> queryAttributes;

    @ManyToOne(optional = false)
    @JoinColumn(name = "study_fk")
    private Study study;

    @OneToOne
    @JoinColumn(name = "metadata_fk")
    private Metadata metadata;

    @Override
    public String toString() {
        return "Series[pk=" + pk
                + ", uid=" + seriesInstanceUID
                + ", no=" + seriesNumber
                + ", mod=" + modality
                + "]";
    }

    @PrePersist
    public void onPrePersist() {
        Date now = new Date();
        createdTime = now;
        updatedTime = now;
        modifiedTime = now;
    }

    @PreUpdate
    public void onPreUpdate() {
        updatedTime = new Date();
    }

    public long getPk() {
        return pk;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public Date getUpdatedTime() {
        return updatedTime;
    }

    public Date getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(Date modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public String getSeriesInstanceUID() {
        return seriesInstanceUID;
    }

    public Integer getSeriesNumber() {
        return seriesNumber;
    }

    public String getSeriesDescription() {
        return seriesDescription;
    }

    public String getModality() {
        return modality;
    }

    public String getSopClassUID() {
        return sopClassUID;
    }

    public void setSopClassUID(String sopClassUID) {
        this.sopClassUID = sopClassUID;
    }

    public String getTransferSyntaxUID() {
        return transferSyntaxUID;
    }

    public void setTransferSyntaxUID(String transferSyntaxUID) {
        this.transferSyntaxUID = transferSyntaxUID;
    }

    public String getInstitutionalDepartmentName() {
        return institutionalDepartmentName;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public String getStationName() {
        return stationName;
    }

    public String getBodyPartExamined() {
        return bodyPartExamined;
    }

    public String getLaterality() {
        return laterality;
    }

    public PersonName getPerformingPhysicianName() {
        return performingPhysicianName;
    }

    public String getPerformedProcedureStepStartDate() {
        return performedProcedureStepStartDate;
    }

    public String getPerformedProcedureStepStartTime() {
        return performedProcedureStepStartTime;
    }

    public String getPerformedProcedureStepInstanceUID() {
        return performedProcedureStepInstanceUID;
    }

    public String getPerformedProcedureStepClassUID() {
        return performedProcedureStepClassUID;
    }

    public String getSeriesCustomAttribute1() {
        return seriesCustomAttribute1;
    }

    public String getSeriesCustomAttribute2() {
        return seriesCustomAttribute2;
    }

    public String getSeriesCustomAttribute3() {
        return seriesCustomAttribute3;
    }

    public String getAccessControlID() {
        return StringUtils.nullify(accessControlID, "*");
    }

    public void setAccessControlID(String accessControlID) {
        this.accessControlID = StringUtils.maskNull(accessControlID, "*");
    }

    public String getSendingAET() {
        return sendingAET;
    }

    public void setSendingAET(String sendingAET) {
        this.sendingAET = sendingAET;
    }

    public String getReceivingAET() {
        return receivingAET;
    }

    public void setReceivingAET(String receivingAET) {
        this.receivingAET = receivingAET;
    }

    public String getSendingPresentationAddress() {
        return sendingPresentationAddress;
    }

    public void setSendingPresentationAddress(String sendingPresentationAddress) {
        this.sendingPresentationAddress = sendingPresentationAddress;
    }

    public String getReceivingPresentationAddress() {
        return receivingPresentationAddress;
    }

    public void setReceivingPresentationAddress(String receivingPresentationAddress) {
        this.receivingPresentationAddress = receivingPresentationAddress;
    }

    public String getSendingHL7Application() {
        return sendingHL7Application;
    }

    public void setSendingHL7Application(String sendingHL7Application) {
        this.sendingHL7Application = sendingHL7Application;
    }

    public String getSendingHL7Facility() {
        return sendingHL7Facility;
    }

    public void setSendingHL7Facility(String sendingHL7Facility) {
        this.sendingHL7Facility = sendingHL7Facility;
    }

    public String getReceivingHL7Application() {
        return receivingHL7Application;
    }

    public void setReceivingHL7Application(String receivingHL7Application) {
        this.receivingHL7Application = receivingHL7Application;
    }

    public String getReceivingHL7Facility() {
        return receivingHL7Facility;
    }

    public void setReceivingHL7Facility(String receivingHL7Facility) {
        this.receivingHL7Facility = receivingHL7Facility;
    }

    public String getExternalRetrieveAET() {
        return externalRetrieveAET;
    }

    public void setExternalRetrieveAET(String externalRetrieveAET) {
        this.externalRetrieveAET = externalRetrieveAET;
    }

    public boolean resetExternalRetrieveAET() {
        if (externalRetrieveAET.equals("*")) return false;
        this.externalRetrieveAET = "*";
        return true;
    }

    public boolean resetSize() {
        if (size == -1) return false;
        this.size = -1L;
        return true;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public RejectionState getRejectionState() {
        return rejectionState;
    }

    public void setRejectionState(RejectionState rejectionState) {
        this.rejectionState = rejectionState;
    }

    public ExpirationState getExpirationState() {
        return expirationState;
    }

    public void setExpirationState(ExpirationState expirationState) {
        this.expirationState = expirationState;
    }

    public LocalDate getExpirationDate() {
        return expirationDate != null ? LocalDate.parse(expirationDate, DateTimeFormatter.BASIC_ISO_DATE) : null;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        if (expirationDate != null) {
            try {
                this.expirationDate = DateTimeFormatter.BASIC_ISO_DATE.format(expirationDate);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else
            this.expirationDate = null;
    }

    public String getExpirationExporterID() {
        return expirationExporterID;
    }

    public void setExpirationExporterID(String expirationExporterID) {
        this.expirationExporterID = expirationExporterID;
    }

    public Date getMetadataScheduledUpdateTime() {
        return metadataScheduledUpdateTime;
    }

    public void setMetadataScheduledUpdateTime(Date metadataScheduledUpdateTime) {
        this.metadataScheduledUpdateTime = metadataScheduledUpdateTime;
    }

    public void scheduleMetadataUpdate(Duration delay, boolean loadObjects) {
        if (delay != null && (metadataScheduledUpdateTime == null || !metadataUpdateLoadObjects && loadObjects)) {
            metadataScheduledUpdateTime = new Date(System.currentTimeMillis() + delay.getSeconds() * 1000L);
            metadataUpdateLoadObjects = loadObjects;
        }
    }

    public boolean isMetadataUpdateLoadObjects() {
        return metadataUpdateLoadObjects;
    }

    public void setMetadataUpdateLoadObjects(boolean metadataUpdateloadObjects) {
        this.metadataUpdateLoadObjects = metadataUpdateloadObjects;
    }

    public int getMetadataUpdateFailures() {
        return metadataUpdateFailures;
    }

    public void setMetadataUpdateFailures(int metadataUpdateFailures) {
        this.metadataUpdateFailures = metadataUpdateFailures;
    }

    public Date getInstancePurgeTime() {
        return instancePurgeTime;
    }

    public void setInstancePurgeTime(Date instancePurgeTime) {
        this.instancePurgeTime = instancePurgeTime;
    }

    public void scheduleInstancePurge(Duration delay) {
        if (delay != null && instancePurgeTime == null)
            instancePurgeTime = new Date(System.currentTimeMillis() + delay.getSeconds() * 1000L);
    }

    public InstancePurgeState getInstancePurgeState() {
        return instancePurgeState;
    }

    public void setInstancePurgeState(InstancePurgeState instancePurgeState) {
        this.instancePurgeState = instancePurgeState;
    }

    public Date getStorageVerificationTime() {
        return storageVerificationTime;
    }

    public void setStorageVerificationTime(Date storageVerificationTime) {
        this.storageVerificationTime = storageVerificationTime;
    }

    public void scheduleStorageVerification(Period delay) {
        if (delay != null && storageVerificationTime == null)
            storageVerificationTime = Date.from(Instant.now().plus(delay));
    }

    public int getFailuresOfLastStorageVerification() {
        return failuresOfLastStorageVerification;
    }

    public void setFailuresOfLastStorageVerification(int failuresOfLastStorageVerification) {
        this.failuresOfLastStorageVerification = failuresOfLastStorageVerification;
    }

    public Date getCompressionTime() {
        return compressionTime;
    }

    public void setCompressionTime(Date compressionTime) {
        this.compressionTime = compressionTime;
    }

    public String getCompressionTransferSyntaxUID() {
        return compressionTransferSyntaxUID;
    }

    public void setCompressionTransferSyntaxUID(String compressionTransferSyntaxUID) {
        this.compressionTransferSyntaxUID = compressionTransferSyntaxUID;
    }

    public Property[] getCompressionImageWriteParams() {
        return parseImageWriteParams(this.compressionImageWriteParams);
    }

    private static Property[] parseImageWriteParams(String params) {
        return params != null
                ? Property.valueOf(StringUtils.split(params, '\\'))
                : new Property[0];
    }

    public void setCompressionImageWriteParams(Property[] imageWriteParams) {
        this.compressionImageWriteParams = imageWriteParams.length > 0
                ? StringUtils.concat(
                    Stream.of(imageWriteParams).map(Property::toString).toArray(String[]::new),
                    '\\')
                : null;
    }

    public int getCompressionFailures() {
        return compressionFailures;
    }

    public void setCompressionFailures(int compressionFailures) {
        this.compressionFailures = compressionFailures;
    }

    public Completeness getCompleteness() {
        return completeness;
    }

    public void setCompleteness(Completeness completeness) {
        this.completeness = completeness;
    }

    public CodeEntity getInstitutionCode() {
        return institutionCode;
    }

    public void setInstitutionCode(CodeEntity institutionCode) {
        this.institutionCode = institutionCode;
    }

    public CodeEntity getInstitutionalDepartmentTypeCode() {
        return institutionalDepartmentTypeCode;
    }

    public void setInstitutionalDepartmentTypeCode(CodeEntity institutionalDepartmentTypeCode) {
        this.institutionalDepartmentTypeCode = institutionalDepartmentTypeCode;
    }

    public Collection<SeriesRequestAttributes> getRequestAttributes() {
        if (requestAttributes == null)
            requestAttributes = new ArrayList<>();

        return requestAttributes;
    }

    public Study getStudy() {
        return study;
    }

    public void setStudy(Study study) {
        this.study = study;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public Attributes getAttributes() throws BlobCorruptedException {
        return attributesBlob.getAttributes();
    }

    public void setAttributes(Attributes attrs, AttributeFilter filter, boolean withOriginalAttributesSequence,
            FuzzyStr fuzzyStr) {
        seriesInstanceUID = attrs.getString(Tag.SeriesInstanceUID);
        seriesNumber = getInt(attrs, Tag.SeriesNumber);
        seriesDescription = attrs.getString(Tag.SeriesDescription, "*");
        institutionName = attrs.getString(Tag.InstitutionName, "*");
        institutionalDepartmentName = attrs.getString(Tag.InstitutionalDepartmentName, "*");
        modality = attrs.getString(Tag.Modality, "*").toUpperCase();
        stationName = attrs.getString(Tag.StationName, "*");
        bodyPartExamined = attrs.getString(Tag.BodyPartExamined, "*").toUpperCase();
        laterality = attrs.getString(Tag.Laterality, "*").toUpperCase();
        Attributes refPPS = attrs.getNestedDataset(Tag.ReferencedPerformedProcedureStepSequence);
        if (refPPS != null) {
            performedProcedureStepInstanceUID = refPPS.getString(Tag.ReferencedSOPInstanceUID, "*");
            performedProcedureStepClassUID = refPPS.getString(Tag.ReferencedSOPClassUID, "*");
        } else {
            performedProcedureStepInstanceUID = "*";
            performedProcedureStepClassUID = "*";
        }
        Date dt = attrs.getDate(Tag.PerformedProcedureStepStartDateAndTime);
        if (dt != null) {
            performedProcedureStepStartDate = DateUtils.formatDA(null, dt);
            performedProcedureStepStartTime = 
                attrs.containsValue(Tag.PerformedProcedureStepStartDate)
                    ? DateUtils.formatTM(null, dt)
                    : "*";
        } else {
            performedProcedureStepStartDate = "*";
            performedProcedureStepStartTime = "*";
        }
        performingPhysicianName = PersonName.valueOf(
                attrs.getString(Tag.PerformingPhysicianName), fuzzyStr,
                performingPhysicianName);
        seriesCustomAttribute1 = 
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute1(), "*");
        seriesCustomAttribute2 =
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute2(), "*");
        seriesCustomAttribute3 =
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute3(), "*");

        Attributes blobAttrs = new Attributes(attrs, filter.getSelection(withOriginalAttributesSequence));
        if (attributesBlob == null)
            attributesBlob = new AttributesBlob(blobAttrs);
        else
            attributesBlob.setAttributes(blobAttrs);
        modifiedTime = new Date();
    }

    private Integer getInt(Attributes attrs, int tag) {
        String val = attrs.getString(tag);
        if (val != null)
            try {
                return Integer.valueOf(val);
            } catch (NumberFormatException e) {
            }
        return null;
    }
}
