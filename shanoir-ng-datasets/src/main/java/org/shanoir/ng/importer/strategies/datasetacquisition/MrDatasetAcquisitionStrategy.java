package org.shanoir.ng.importer.strategies.datasetacquisition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.shanoir.ng.dataset.Dataset;
import org.shanoir.ng.dataset.modality.MrDataset;
import org.shanoir.ng.datasetacquisition.DatasetAcquisition;
import org.shanoir.ng.datasetacquisition.mr.MrDatasetAcquisition;
import org.shanoir.ng.datasetacquisition.mr.MrProtocol;
import org.shanoir.ng.dicom.DicomProcessing;
import org.shanoir.ng.importer.dto.DatasetsWrapper;
import org.shanoir.ng.importer.dto.ImportJob;
import org.shanoir.ng.importer.dto.Serie;
import org.shanoir.ng.importer.strategies.dataset.DatasetStrategy;
import org.shanoir.ng.importer.strategies.protocol.MrProtocolStrategy;
import org.shanoir.ng.shared.model.EchoTime;
import org.shanoir.ng.shared.model.FlipAngle;
import org.shanoir.ng.shared.model.InversionTime;
import org.shanoir.ng.shared.model.RepetitionTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 
 * MR Dataset Acquisition Strategy used to create new Mr Dataset Acquisition.
 * Called by the ImportService. Requires an importJob
 * 
 * Refer to Interface for more information
 * 
 * @author atouboul
 *
 */
@Component
public class MrDatasetAcquisitionStrategy implements DatasetAcquisitionStrategy {
	
	/** Logger. */
	private static final Logger LOG = LoggerFactory.getLogger(MrDatasetAcquisitionStrategy.class);

	@Autowired
	DicomProcessing dicomProcessing;
	
	@Autowired
	MrProtocolStrategy mrProtocolStrategy;
	
	@Autowired
	DatasetStrategy<Dataset> mrDatasetStrategy;
	
	@Override
	public DatasetAcquisition generateDatasetAcquisitionForSerie(Serie serie, int rank, ImportJob importJob) {
		MrDatasetAcquisition mrDatasetAcquisition = new MrDatasetAcquisition();
		Attributes dicomAttributes = null;
		try {
			// TODO ATO : should always be a dicom: add check
			dicomAttributes = dicomProcessing.getDicomObjectAttributes(serie.getFirstDatasetFileForCurrentSerie());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			LOG.error("Unable to retrieve dicom attributes in file " + serie.getFirstDatasetFileForCurrentSerie().getPath(),e); 
		}
		mrDatasetAcquisition.setRank(rank);
		mrDatasetAcquisition.setSortingIndex(serie.getSeriesNumber());
		mrDatasetAcquisition.setSoftwareRelease(dicomAttributes.getString(Tag.SoftwareVersions));		
		// TODO ATO : replace 1L with equipment contained in Study Card
		mrDatasetAcquisition.setAcquisitionEquipmentId(1L);
		
		MrProtocol mrProtocol = mrProtocolStrategy.generateMrProtocolForSerie(dicomAttributes, serie);
		mrDatasetAcquisition.setMrProtocol(mrProtocol);
	
		// TODO ATO add Compatibility check between study card Equipment and dicomEquipment if not done at front level. 
		DatasetsWrapper<Dataset> datasetsWrapper = mrDatasetStrategy.generateDatasetsForSerie(dicomAttributes, serie, importJob);
		mrDatasetAcquisition.setDatasets(datasetsWrapper.getDatasets());
		
		// total acquisition time
		if(mrDatasetAcquisition.getMrProtocol().getAcquisitionDuration() == null) {
			Double totalAcquisitionTime = null;
			if (datasetsWrapper.getFirstImageAcquisitionTime() != null && datasetsWrapper.getLastImageAcquisitionTime() != null) {
				totalAcquisitionTime = new Double(datasetsWrapper.getLastImageAcquisitionTime().getTime() - datasetsWrapper.getFirstImageAcquisitionTime().getTime());
				mrDatasetAcquisition.getMrProtocol().setAcquisitionDuration(totalAcquisitionTime);
			} else {
				mrDatasetAcquisition.getMrProtocol().setAcquisitionDuration(null);
			}
		}
		
		// building EchoTime, InversionTime, FlipAngle, RepetitionTime for MrProcotol based on dataset
		Map<Integer,EchoTime> echoTimes = new HashMap<>(); 
		Map<Double,FlipAngle> flipAngles = new HashMap<>();  
		Map<Double,InversionTime> inversionTimes = new HashMap<>();
		Map<Double,RepetitionTime> repetitionTimes = new HashMap<>();
		
		for (Object dataset : datasetsWrapper.getDatasets()) {
			MrDataset mrDataset = (MrDataset) dataset;
			echoTimes.putAll(mrDataset.getEchoTimes());
			flipAngles.putAll(mrDataset.getFlipAngles());
			inversionTimes.putAll(mrDataset.getInversionTimes());
			repetitionTimes.putAll(mrDataset.getRepetitionTimes());
			// as all datasets are iterated here, set link to acquisition
			mrDataset.setDatasetAcquisition(mrDatasetAcquisition);
		}
		
		mrDatasetAcquisition.getMrProtocol().setEchoTimes(new ArrayList<EchoTime>(echoTimes.values()));
		mrDatasetAcquisition.getMrProtocol().setRepetitionTimeList(new ArrayList<RepetitionTime>(repetitionTimes.values()));
		mrDatasetAcquisition.getMrProtocol().setFlipAngles(new ArrayList<FlipAngle>(flipAngles.values()));
		mrDatasetAcquisition.getMrProtocol().setInversionTimeList(new ArrayList<InversionTime>(inversionTimes.values()));
		
		return mrDatasetAcquisition;
	}
	
	

}
