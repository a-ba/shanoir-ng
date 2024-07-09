/**
 * Shanoir NG - Import, manage and share neuroimaging data
 * Copyright (C) 2009-2019 Inria - https://www.inria.fr/
 * Contact us on https://project.inria.fr/shanoir/
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/gpl-3.0.html
 */

/**
 * NOTE: This class is auto generated by the swagger code generator program (2.2.3).
 * https://github.com/swagger-api/swagger-codegen
 * Do not edit the class manually.
 */
package org.shanoir.ng.solr.service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.solr.client.solrj.SolrServerException;
import org.shanoir.ng.dataset.repository.DatasetRepository;
import org.shanoir.ng.examination.repository.ExaminationRepository;
import org.shanoir.ng.shared.dateTime.DateTimeUtils;
import org.shanoir.ng.shared.event.ShanoirEvent;
import org.shanoir.ng.shared.event.ShanoirEventService;
import org.shanoir.ng.shared.event.ShanoirEventType;
import org.shanoir.ng.shared.exception.RestServiceException;
import org.shanoir.ng.shared.model.Center;
import org.shanoir.ng.shared.paging.PageImpl;
import org.shanoir.ng.shared.repository.CenterRepository;
import org.shanoir.ng.shared.repository.SubjectStudyRepository;
import org.shanoir.ng.shared.subjectstudy.SubjectType;
import org.shanoir.ng.solr.model.ShanoirMetadata;
import org.shanoir.ng.solr.model.ShanoirSolrDocument;
import org.shanoir.ng.solr.model.ShanoirSolrQuery;
import org.shanoir.ng.solr.repository.ShanoirMetadataRepository;
import org.shanoir.ng.solr.solrj.SolrJWrapper;
import org.shanoir.ng.study.rights.StudyUser;
import org.shanoir.ng.study.rights.StudyUserRightsRepository;
import org.shanoir.ng.utils.KeycloakUtil;
import org.shanoir.ng.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.solr.core.query.result.SolrResultPage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * @author yyao
 *
 */
@Service
public class SolrServiceImpl implements SolrService {
	
	@Autowired
	private SolrJWrapper solrJWrapper;

	@Autowired
	private ShanoirMetadataRepository shanoirMetadataRepository;

	@Autowired
	private StudyUserRightsRepository rightsRepository;

	@Autowired
	private SubjectStudyRepository subjectStudyRepo;

	@Autowired
	private CenterRepository centerRepository;

	@Autowired
	private ExaminationRepository examRepository;

	@Autowired
	private DatasetRepository dsRepository;

	@Autowired
	private ShanoirEventService eventService;

	private static final Logger LOG = LoggerFactory.getLogger(SolrServiceImpl.class);

	public void addToIndex (final ShanoirSolrDocument document) throws SolrServerException, IOException {
		solrJWrapper.addToIndex(document);
	}

	public void addAllToIndex (final List<ShanoirSolrDocument> documents) throws SolrServerException, IOException {
		solrJWrapper.addAllToIndex(documents);
	}

	public void deleteFromIndex(Long datasetId) throws SolrServerException, IOException {
		solrJWrapper.deleteFromIndex(datasetId);
	}

	public void deleteFromIndex(List<Long> datasetIds) throws SolrServerException, IOException {
		solrJWrapper.deleteFromIndex(datasetIds);
	}

	public void deleteAll() throws SolrServerException, IOException {
		solrJWrapper.deleteAll();
	}


	@Override
	@Async
	@Transactional
	@Scheduled(cron = "0 0 6 * * *", zone="Europe/Paris")
	public void indexAll() {

		ShanoirEvent event = new ShanoirEvent(
				ShanoirEventType.SOLR_INDEX_ALL_EVENT,
				null,
				KeycloakUtil.getTokenUserId(),
				"Cleaning Solr index...",
				ShanoirEvent.IN_PROGRESS,
				0f);
		eventService.publishEvent(event);
        try {
            deleteAll();
        } catch (SolrServerException | IOException e) {
			LOG.error("Error while cleaning Solr index.", e);
			eventService.publishErrorEvent(event, "Error while cleaning Solr index : " + e.getMessage());
			return;
        }
		eventService.publishEvent(event, "Fetching data to index...", 0.25f);

		List<ShanoirMetadata> documents;
		Map<Long, List<String>> tags;
		try {
			documents = shanoirMetadataRepository.findAllAsSolrDoc();
			eventService.publishEvent(event, "Fetching data to index...", 0.5f);
			tags = shanoirMetadataRepository.findAllTags(null);
			eventService.publishEvent(event, "Fetching data to index...", 0.75f);
		} catch(Exception e){
			LOG.error("Error while fetching data to index.", e);
			eventService.publishErrorEvent(event, "Error while fetching data to index : " + e.getMessage());
			return;
		}

		try {
            this.indexDocumentsInSolr(documents, tags, event);
        } catch (SolrServerException | IOException e) {
			LOG.error("Error indexing datasets into Solr.", e);
			eventService.publishErrorEvent(event, "Error indexing datasets into Solr : " + e.getMessage());
        }
    }

	@Transactional
	@Override
	public void indexDatasets(List<Long> datasetIds) throws SolrServerException, IOException {
		// Get all associated datasets and index them to solr
		List<ShanoirMetadata> metadatas = shanoirMetadataRepository.findSolrDocs(datasetIds);
		Map<Long, List<String>> tags = shanoirMetadataRepository.findAllTags(datasetIds);
		this.indexDocumentsInSolr(metadatas, tags, null);
	}

	@Override
	@Transactional(isolation = Isolation.READ_UNCOMMITTED,  propagation = Propagation.REQUIRES_NEW)
	public void indexDataset(Long datasetId) throws SolrServerException, IOException {
		ShanoirMetadata shanoirMetadata = shanoirMetadataRepository.findOneSolrDoc(datasetId);
		if (shanoirMetadata == null) throw new IllegalStateException("shanoir metadata with id " +  datasetId + " query failed to return any result");
		ShanoirSolrDocument doc = getShanoirSolrDocument(shanoirMetadata);
		Map<Long, List<String>> tags = shanoirMetadataRepository.findAllTags(Collections.singletonList(datasetId));
		doc.setTags(tags.get(datasetId));
		solrJWrapper.addToIndex(doc);
	}

	private void indexDocumentsInSolr(List<ShanoirMetadata> metadatas, Map<Long, List<String>> tags, ShanoirEvent event) throws SolrServerException, IOException {

		int docNb = metadatas.size();

		if(event != null){
			eventService.publishEvent(event, "Indexed [0/" + docNb + "] datasets.", event.getProgress());
		}

		Iterator<ShanoirMetadata> docIt = metadatas.iterator();

		List<ShanoirSolrDocument> solrDocuments = new ArrayList<>();

		while (docIt.hasNext()) {
			ShanoirMetadata shanoirMetadata = docIt.next();

			ShanoirSolrDocument doc = this.getShanoirSolrDocument(shanoirMetadata);
			doc.setTags(tags.get(shanoirMetadata.getDatasetId()));
			solrDocuments.add(doc);
		}
		solrJWrapper.addAllToIndex(solrDocuments);
		if(event != null){
			eventService.publishSuccessEvent(event, "Indexed [" + docNb + "/" + docNb + "] datasets.");
		}
	}

	private ShanoirSolrDocument getShanoirSolrDocument(ShanoirMetadata shanoirMetadata) {
		return new ShanoirSolrDocument(String.valueOf(shanoirMetadata.getDatasetId()), shanoirMetadata.getDatasetId(), shanoirMetadata.getDatasetName(),
				shanoirMetadata.getDatasetType(), shanoirMetadata.getDatasetNature(), DateTimeUtils.localDateToDate(shanoirMetadata.getDatasetCreationDate()),
				shanoirMetadata.getExaminationId(), shanoirMetadata.getExaminationComment(), DateTimeUtils.localDateToDate(shanoirMetadata.getExaminationDate()), shanoirMetadata.getAcquisitionEquipmentName(),
				shanoirMetadata.getSubjectName(), SubjectType.getType(shanoirMetadata.getSubjectType()) != null ? SubjectType.getType(shanoirMetadata.getSubjectType()).name() : null, shanoirMetadata.getSubjectId(), shanoirMetadata.getStudyName(), shanoirMetadata.getStudyId(), shanoirMetadata.getCenterName(),
				shanoirMetadata.getCenterId(), shanoirMetadata.getSliceThickness(), shanoirMetadata.getPixelBandwidth(), shanoirMetadata.getMagneticFieldStrength(),
				shanoirMetadata.isProcessed());
	}

	@Transactional
	@Override
	public SolrResultPage<ShanoirSolrDocument> facetSearch(ShanoirSolrQuery query, Pageable pageable) throws RestServiceException {
		SolrResultPage<ShanoirSolrDocument> result;
		pageable = prepareTextFields(pageable);
		if (KeycloakUtil.getTokenRoles().contains("ROLE_ADMIN")) {
			result = solrJWrapper.findByFacetCriteriaForAdmin(query, pageable);
		} else {
			Map<Long, List<String>> studiesCenter = getStudiesCenter();
			result = solrJWrapper.findByStudyIdInAndFacetCriteria(studiesCenter, query, pageable);
		}
		return result;
	}

	private Map<Long, List<String>> getStudiesCenter() {
		List<StudyUser> studyUsers = Utils.toList(rightsRepository.findByUserId(KeycloakUtil.getTokenUserId()));
		Map<Long, List<String>> studiesCenter = new HashMap<>();
		List<Center> centers = Utils.toList(centerRepository.findAll());
		for(StudyUser su : studyUsers) {
			if (su.isConfirmed()) {
				studiesCenter.put(su.getStudyId(), su.getCenterIds().stream().map(centerId -> findCenterName(centers, centerId)).collect(Collectors.toList()));
			}
		}
		return studiesCenter;
	}
	
	private String findCenterName(List<Center> centers, Long id) {
		List<Center> filteredCenters = centers.stream().filter(centerToFilter -> centerToFilter.getId().equals(id)).collect(Collectors.toList());
		return filteredCenters.size() > 0 ? filteredCenters.get(0).getName() : null;
	}

	private Pageable prepareTextFields(Pageable pageable) {
		for (Sort.Order order : pageable.getSort()) {
			if (order.getProperty().equals("studyName") || order.getProperty().equals("subjectName")
					|| order.getProperty().equals("datasetName") || order.getProperty().equals("datasetNature")
					|| order.getProperty().equals("datasetType") || order.getProperty().equals("examinationComment")
					|| order.getProperty().equals("tags") || order.getProperty().equals("subjectType") || order.getProperty().equals("acquisitionEquipmentName")
					|| order.getProperty().equals("processed")
			) {
				pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
						order.getDirection(), order.getProperty());
			} else if (order.getProperty().equals("id")) {
				pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
						order.getDirection(), "datasetId");
			}
		}
		return pageable;
	}

	@Override
	public Page<ShanoirSolrDocument> getByIdIn(List<Long> datasetIds, Pageable pageable) throws RestServiceException {
		if (datasetIds.isEmpty()) {
			return new PageImpl<>();
		}
		Page<ShanoirSolrDocument> result;
		pageable = prepareTextFields(pageable);
		if (KeycloakUtil.getTokenRoles().contains("ROLE_ADMIN")) {
			result = solrJWrapper.findByDatasetIdIn(datasetIds, pageable);
		} else {
			Map<Long, List<String>> studiesCenter = getStudiesCenter();
			result = solrJWrapper.findByStudyIdInAndDatasetIdIn(studiesCenter, datasetIds, pageable);
		}
		return result;
	}

	/**
	 * Updates a list of datasets in Solr.
	 * @param datasetIds the list of dataset IDs to update
	 */
	@Override
	public void updateDatasets(List<Long> datasetIds) throws SolrServerException, IOException {
		if (CollectionUtils.isEmpty(datasetIds)) {
			return;
		}
		this.deleteFromIndex(datasetIds);
		this.indexDatasets(datasetIds);		
	}

	@Override
	@Async
	public void updateDatasetsAsync(List<Long> datasetIds) throws SolrServerException, IOException {
		this.updateDatasets(datasetIds);
	}

	@Override
	@Async
	@Transactional
	public void updateSubjectsAsync(List<Long> subjectIds) throws SolrServerException, IOException {
		List<Long> ids = this.dsRepository.findIdsBySubjectIdIn(subjectIds);
		this.updateDatasets(ids);
	}

	@Override
	@Async
	public void updateStudyAsync(Long studyId) throws SolrServerException, IOException {
		List<Long> ids = this.dsRepository.findIdsByStudyId(studyId);
		this.updateDatasets(ids);
	}

}
