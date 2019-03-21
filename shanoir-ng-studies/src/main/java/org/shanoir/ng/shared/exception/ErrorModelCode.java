package org.shanoir.ng.shared.exception;

/**
 * @author msimon
 *
 */
public final class ErrorModelCode {

	/** Login - bad credentials */
	public static final Integer SC_MS_COMM_FAILURE = 51;
	
	/** Login - bad credentials */
	public static final Integer BAD_CREDENTIALS = 101;
	
	/** Login - date expired */
	public static final Integer DATE_EXPIRED = 102;
	
	/** No study found */
	public static final Integer STUDY_NOT_FOUND = 201;
	
	/** No center found */
	public static final Integer CENTER_NOT_FOUND = 211;
	
	/** No acquisition equipment found */
	public static final Integer ACQ_EQPT_NOT_FOUND = 251;
	
	/** No manufacturer model found */
	public static final Integer MANUFACTURER_MODEL_NOT_FOUND = 261;
	
	/** No manufacturer found */
	public static final Integer MANUFACTURER_NOT_FOUND = 271;
	
}