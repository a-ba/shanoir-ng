package org.shanoir.uploader.action;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ResourceBundle;
import java.util.Set;

import javax.swing.JOptionPane;

import org.apache.log4j.Logger;
import org.shanoir.ng.exchange.imports.subject.IdentifierCalculator;
import org.shanoir.ng.importer.dicom.ImagesCreatorAndDicomFileAnalyzerService;
import org.shanoir.uploader.ShUpConfig;
import org.shanoir.uploader.dicom.IDicomServerClient;
import org.shanoir.uploader.dicom.anonymize.Pseudonymizer;
import org.shanoir.uploader.dicom.query.SerieTreeNode;
import org.shanoir.uploader.exception.PseudonymusException;
import org.shanoir.uploader.gui.MainWindow;
import org.shanoir.uploader.utils.Util;

/**
 * This class implements the logic when the download or copy button is clicked.
 * 
 * @author mkain
 * 
 */
public class DownloadOrCopyActionListener implements ActionListener {

	private static Logger logger = Logger.getLogger(DownloadOrCopyActionListener.class);

	private MainWindow mainWindow;
	private ResourceBundle resourceBundle;
	private Pseudonymizer pseudonymizer;
	private IdentifierCalculator identifierCalculator;
	
	// Introduced here to inject into DownloadOrCopyRunnable
	private IDicomServerClient dicomServerClient;
	
	private ImagesCreatorAndDicomFileAnalyzerService dicomFileAnalyzer;

	public DownloadOrCopyActionListener(final MainWindow mainWindow, final Pseudonymizer pseudonymizer, final IDicomServerClient dicomServerClient, final ImagesCreatorAndDicomFileAnalyzerService dicomFileAnalyzer) {
		this.mainWindow = mainWindow;
		this.resourceBundle = mainWindow.resourceBundle;
		this.pseudonymizer = pseudonymizer;
		this.identifierCalculator = new IdentifierCalculator();
		this.dicomServerClient = dicomServerClient;
		this.dicomFileAnalyzer = dicomFileAnalyzer;
	}

	/**
	 * This method contains all the logic which is performed when the download from PACS
	 * or copy from CD/DVD button is clicked.
	 */
	public void actionPerformed(final ActionEvent event) {
		if (mainWindow.dicomTree == null) {
			return;
		}
		/**
		 * 1. Read values from GUI, entered by user
		 */
		DicomDataTransferObject dicomData = mainWindow.getSAL().getDicomData();
		dicomData = completeDicomDataWithGUIValues(dicomData);
		if (dicomData == null) {
			return;
		}
		/**
		 * 2. Generate subject identifier and hash values
		 */
		try {
			dicomData = generateSubjectIdentifierAndHashValues(dicomData);
		} catch (PseudonymusException e) {
			logger.error(e.getMessage(), e);
			JOptionPane.showMessageDialog(mainWindow.frame,
				    resourceBundle.getString("shanoir.uploader.systemErrorDialog.error.phv"),
				    resourceBundle.getString("shanoir.uploader.select.error.title"),
				    JOptionPane.ERROR_MESSAGE);
			return;
		} catch (UnsupportedEncodingException e) {
			logger.error(e.getMessage(), e);
			JOptionPane.showMessageDialog(mainWindow.frame,
				    resourceBundle.getString("shanoir.uploader.systemErrorDialog.error.phv"),
				    resourceBundle.getString("shanoir.uploader.select.error.title"),
				    JOptionPane.ERROR_MESSAGE);
			return;
		} catch (NoSuchAlgorithmException e) {
			logger.error(e.getMessage(), e);
			JOptionPane.showMessageDialog(mainWindow.frame,
				    resourceBundle.getString("shanoir.uploader.systemErrorDialog.error.phv"),
				    resourceBundle.getString("shanoir.uploader.select.error.title"),
				    JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		/**
		 * 3. Download from PACS or copy from CD/DVD and write upload-job.xml + nominative-data-job.xml
		 */
		final String filePathDicomDir = mainWindow.getFindDicomActionListener().getFilePathDicomDir();
		final Set<SerieTreeNode> selectedSeries = mainWindow.getSAL().getSelectedSeries();
		Runnable runnable = new DownloadOrCopyRunnable(mainWindow.isFromPACS, dicomServerClient, dicomFileAnalyzer,  filePathDicomDir, selectedSeries, dicomData);
		Thread thread = new Thread(runnable);
		thread.start();
		
		// clear previous selection, but keep tree open in the tab
		mainWindow.isDicomObjectSelected = false;
		mainWindow.dicomTree.getSelectionModel().clearSelection();
		mainWindow.getSAL().setDicomData(null);
		mainWindow.getSAL().setSelectedSeries(null);
	
		JOptionPane.showMessageDialog(mainWindow.frame,
			    resourceBundle.getString("shanoir.uploader.downloadOrCopy.confirmation.message"),
			    resourceBundle.getString("shanoir.uploader.downloadOrCopy.confirmation.title"),
			    JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * This method reads the data entered by the user with the GUI
	 * and puts it into the DicomDataTransferObject, when the user
	 * clicks on the download or copy button.
	 * 
	 * @param dicomData
	 * @return
	 */
	private DicomDataTransferObject completeDicomDataWithGUIValues(final DicomDataTransferObject dicomData) {
		try {
			dicomData.setBirthDate(Util.convertStringToLocalDate(mainWindow.birthDateTF.getText()));
			if (mainWindow.mSexR.isSelected())
				dicomData.setSex("M");
			if (mainWindow.fSexR.isSelected())
				dicomData.setSex("F");
			return completeDicomData(dicomData);					
		} catch (ParseException e) {
			logger.error("Unable to convert BirthDate using formatter", e);
			return null;
		}
	}

	private DicomDataTransferObject completeDicomData(final DicomDataTransferObject dicomData) throws ParseException {
		if (mainWindow.lastNameTF.getText().isEmpty()) {
			JOptionPane.showMessageDialog(mainWindow.frame,
				    resourceBundle.getString("shanoir.uploader.import.start.lastname.empty"),
				    resourceBundle.getString("shanoir.uploader.select.error.title"),
				    JOptionPane.ERROR_MESSAGE);
			return null;
		}
		dicomData.setLastName(mainWindow.lastNameTF.getText());
		if (mainWindow.firstNameTF.getText().isEmpty()) {
			JOptionPane.showMessageDialog(mainWindow.frame,
				    resourceBundle.getString("shanoir.uploader.import.start.firstname.empty"),
				    resourceBundle.getString("shanoir.uploader.select.error.title"),
				    JOptionPane.ERROR_MESSAGE);
			return null;
		}
		dicomData.setFirstName(mainWindow.firstNameTF.getText());
		if (mainWindow.birthNameTF.getText().isEmpty()) {
			JOptionPane.showMessageDialog(mainWindow.frame,
				    resourceBundle.getString("shanoir.uploader.import.start.birthname.empty"),
				    resourceBundle.getString("shanoir.uploader.select.error.title"),
				    JOptionPane.ERROR_MESSAGE);
			return null;
		}
		dicomData.setBirthName(mainWindow.birthNameTF.getText());
		return dicomData;
	}

	private DicomDataTransferObject generateSubjectIdentifierAndHashValues(DicomDataTransferObject dicomData) throws PseudonymusException, UnsupportedEncodingException, NoSuchAlgorithmException {
		String subjectIdentifier = null;
		// OFSEP mode
		if (ShUpConfig.isModePseudonymus()) {
			dicomData = pseudonymizer.createHashValuesWithPseudonymus(dicomData);
			subjectIdentifier = identifierCalculator.calculateIdentifierWithHashs(dicomData.getFirstNameHash1(), dicomData.getBirthNameHash1(), dicomData.getBirthDateHash());
		// Neurinfo mode
		} else {
			String birthDate = Util.convertLocalDateToString(dicomData.getBirthDate());
			subjectIdentifier = identifierCalculator.calculateIdentifier(dicomData.getFirstName(), dicomData.getLastName(), birthDate);
		}
		dicomData.setSubjectIdentifier(subjectIdentifier);
		return dicomData;
	}

}
