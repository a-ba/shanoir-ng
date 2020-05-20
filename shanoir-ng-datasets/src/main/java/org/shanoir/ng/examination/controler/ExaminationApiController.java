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

package org.shanoir.ng.examination.controler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.apache.commons.io.FileUtils;
import org.shanoir.ng.examination.dto.ExaminationDTO;
import org.shanoir.ng.examination.dto.SubjectExaminationDTO;
import org.shanoir.ng.examination.dto.mapper.ExaminationMapper;
import org.shanoir.ng.examination.model.Examination;
import org.shanoir.ng.examination.service.ExaminationService;
import org.shanoir.ng.exporter.service.BIDSService;
import org.shanoir.ng.shared.error.FieldErrorMap;
import org.shanoir.ng.shared.event.ShanoirEvent;
import org.shanoir.ng.shared.event.ShanoirEventService;
import org.shanoir.ng.shared.event.ShanoirEventType;
import org.shanoir.ng.shared.exception.EntityNotFoundException;
import org.shanoir.ng.shared.exception.ErrorDetails;
import org.shanoir.ng.shared.exception.ErrorModel;
import org.shanoir.ng.shared.exception.RestServiceException;
import org.shanoir.ng.utils.KeycloakUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.annotations.ApiParam;

@Controller
public class ExaminationApiController implements ExaminationApi {

	private static final Logger LOG = LoggerFactory.getLogger(ExaminationApiController.class);

	@Autowired
	private ExaminationMapper examinationMapper;

	@Autowired
	private ExaminationService examinationService;

	@Value("${datasets-data}")
	private String dataDir;

	private final HttpServletRequest request;


	@Autowired
	BIDSService bidsService;

	@Autowired
	ShanoirEventService eventService;

	@org.springframework.beans.factory.annotation.Autowired
	public ExaminationApiController(final HttpServletRequest request) {
		this.request = request;
	}

	@Override
	public ResponseEntity<Void> deleteExamination(
			@ApiParam(value = "id of the examination", required = true) @PathVariable("examinationId") final Long examinationId)
					throws RestServiceException {
		try {
			// delete bids folder
			bidsService.deleteExam(examinationId);
			// Check if user rights needed
			examinationService.deleteById(examinationId);

			// Delete extra data
			String dataPath = getExtraDataFilePath(examinationId, "");
			File fileToDelete = new File(dataPath);
			if (fileToDelete.exists()) {
				FileUtils.deleteDirectory(fileToDelete);
			}
			eventService.publishEvent(new ShanoirEvent(ShanoirEventType.DELETE_EXAMINATION_EVENT, examinationId.toString(), KeycloakUtil.getTokenUserId(), "", ShanoirEvent.SUCCESS));
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		} catch (EntityNotFoundException e) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		} catch (IOException e) {
			LOG.error("Something went wrong while deleting extra-data file: {}" , e);
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		}
	}

	@Override
	public ResponseEntity<ExaminationDTO> findExaminationById(
			@ApiParam(value = "id of the examination", required = true) @PathVariable("examinationId") final Long examinationId)
					throws RestServiceException {

		Examination examination = examinationService.findById(examinationId);
		if (examination == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		return new ResponseEntity<>(examinationMapper.examinationToExaminationDTO(examination), HttpStatus.OK);
	}

	@Override
	public ResponseEntity<Page<ExaminationDTO>> findExaminations(final Pageable pageable) {
		Page<Examination> examinations = examinationService.findPage(pageable);
		if (examinations.getContent().isEmpty()) {
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		}
		return new ResponseEntity<>(examinationMapper.examinationsToExaminationDTOs(examinations), HttpStatus.OK);
	}
	
	@Override
	public ResponseEntity<Page<ExaminationDTO>> findPreclinicalExaminations(
			@ApiParam(value = "preclinical", required = true) @PathVariable("isPreclinical") Boolean isPreclinical, Pageable pageable) {
		Page<Examination> examinations;

		// Get examinations reachable by connected user
		examinations = examinationService.findPreclinicalPage(isPreclinical, pageable);
		if (examinations.getContent().isEmpty()) {
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		}
		return new ResponseEntity<>(examinationMapper.examinationsToExaminationDTOs(examinations), HttpStatus.OK);
	}

	@Override
	public ResponseEntity<List<SubjectExaminationDTO>> findExaminationsBySubjectIdStudyId(
			@ApiParam(value = "id of the subject", required = true) @PathVariable("subjectId") Long subjectId,
			@ApiParam(value = "id of the study", required = true) @PathVariable("studyId") Long studyId) {

		final List<Examination> examinations = examinationService.findBySubjectIdStudyId(subjectId, studyId);
		if (examinations.isEmpty()) {
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		}
		return new ResponseEntity<>(examinationMapper.examinationsToSubjectExaminationDTOs(examinations), HttpStatus.OK);
	}

	@Override
	public ResponseEntity<ExaminationDTO> saveNewExamination(
			@ApiParam(value = "the examination to create", required = true) @RequestBody @Valid final ExaminationDTO examinationDTO,
			final BindingResult result) throws RestServiceException {
		validate(result);
		final Examination createdExamination = examinationService.save(examinationDTO);
		eventService.publishEvent(new ShanoirEvent(ShanoirEventType.CREATE_EXAMINATION_EVENT, createdExamination.getId().toString(), KeycloakUtil.getTokenUserId(), "", ShanoirEvent.SUCCESS));
		return new ResponseEntity<>(examinationMapper.examinationToExaminationDTO(createdExamination), HttpStatus.OK);
	}

	@Override
	public ResponseEntity<Void> updateExamination(
			@ApiParam(value = "id of the examination", required = true) @PathVariable("examinationId") final Long examinationId,
			@ApiParam(value = "the examination to update", required = true) @RequestBody @Valid final ExaminationDTO examination,
			final BindingResult result) throws RestServiceException {
		/* Update examination in db. */
		try {
			examinationService.update(examinationMapper.examinationDTOToExamination(examination));
			eventService.publishEvent(new ShanoirEvent(ShanoirEventType.UPDATE_EXAMINATION_EVENT, examinationId.toString(), KeycloakUtil.getTokenUserId(), "", ShanoirEvent.SUCCESS));
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		} catch (EntityNotFoundException e) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@Override
	public ResponseEntity<List<ExaminationDTO>> findExaminationsBySubjectId(@ApiParam(value = "id of the subject", required = true) @PathVariable("subjectId") Long subjectId) {
		final List<Examination> examinations = examinationService.findBySubjectId(subjectId);
		if (examinations.isEmpty()) {
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		}
		return new ResponseEntity<>(examinationMapper.examinationsToExaminationDTOs(examinations),
				HttpStatus.OK);
	}

	@Override
	public ResponseEntity<Void> addExtraData(
			@ApiParam(value = "id of the examination", required = true) @PathVariable("examinationId") Long examinationId,
			@ApiParam(value = "file to upload", required = true) @Valid @RequestBody MultipartFile file) throws RestServiceException {
		String filePath = getExtraDataFilePath(examinationId, file.getOriginalFilename());
		File fileToCreate = new File(filePath);
		fileToCreate.getParentFile().mkdirs();
		try {
			LOG.info("Saving file {} to destination: {}", file.getOriginalFilename(), filePath);
			file.transferTo(new File(filePath));
		} catch (Exception e) {
			LOG.error("Error while loading files on examination: {}. File not uploaded. {}", examinationId, e);
		}
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@Override
	public 	ResponseEntity<ByteArrayResource> downloadExtraData(
			@ApiParam(value = "id of the examination", required = true) @PathVariable("examinationId") Long examinationId,
			@ApiParam(value = "file to download", required = true) @PathVariable("fileName") String fileName) throws RestServiceException, IOException {
		String filePath = getExtraDataFilePath(examinationId, fileName);
		LOG.info("Retrieving file : {}", filePath);
		File fileToDownLoad = new File(filePath);
		if (!fileToDownLoad.exists()) {
			return new ResponseEntity<>(null, HttpStatus.NO_CONTENT);
		}

		byte[] data = Files.readAllBytes(fileToDownLoad.toPath());
		ByteArrayResource resource = new ByteArrayResource(data);

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + fileToDownLoad.getName())
				.contentType(MediaType.APPLICATION_PDF)
				.contentLength(data.length)
				.body(resource);
	}

	/**
	 * Gets the extra data file path
	 * @param examinationId id of the examination
	 * @param fileName name of the file
	 * @return the file path of the file
	 */
	private String getExtraDataFilePath(Long examinationId, String fileName) {
		return dataDir + "/examination-" + examinationId + "/" + fileName;
	}

	@Override
	public ResponseEntity<ByteArrayResource> exportExaminationById(@ApiParam(value = "id of the examination", required = true) @PathVariable("examinationId") Long examinationId)
			throws RestServiceException {
		// Get examination from ID
		Examination exam = examinationService.findById(examinationId);
		if (exam.getExtraDataFilePathList() == null) {
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		}

		String archiveToExport = null;
		for (String extraDataFile : exam.getExtraDataFilePathList()) {
			if (extraDataFile.startsWith(dataDir + "/preclinical/")) {
				archiveToExport = extraDataFile;
				break;
			}
		}
		if (archiveToExport == null) {
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		}
		File archiveFile = new File(archiveToExport);
		
		byte[] data;
		try {
			data = Files.readAllBytes(archiveFile.toPath());
		} catch (IOException e) {
			LOG.error(e.getMessage());
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
		ByteArrayResource resource = new ByteArrayResource(data);

		// Try to determine file's content type
		String contentType = request.getServletContext().getMimeType(archiveFile.getAbsolutePath());
		return ResponseEntity.ok()
				// Content-Disposition
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + archiveFile.getName())
				// Content-Type
				.contentType(MediaType.parseMediaType(contentType)) //
				// Content-Length
				.contentLength(data.length) //
				.body(resource);
	}

	/**
	 * Validate a dataset
	 * 
	 * @param result
	 * @throws RestServiceException
	 */
	private void validate(BindingResult result) throws RestServiceException {
		final FieldErrorMap errors = new FieldErrorMap(result);
		if (!errors.isEmpty()) {
			ErrorModel error = new ErrorModel(HttpStatus.UNPROCESSABLE_ENTITY.value(), "Bad arguments", new ErrorDetails(errors));
			throw new RestServiceException(error);
		}
	}

}