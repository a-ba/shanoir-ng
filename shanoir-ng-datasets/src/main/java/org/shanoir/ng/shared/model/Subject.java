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
package org.shanoir.ng.shared.model;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.shanoir.ng.shared.core.model.IdName;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author yyao
 *
 */
@Entity
@Table(name = "subject")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Subject extends IdName {

	/** Relations beetween the subjects and the studies. */
	@OneToMany(cascade = CascadeType.ALL, mappedBy = "subject", fetch = FetchType.LAZY, orphanRemoval = true)
	private List<SubjectStudy> subjectStudyList;
	
	@Id
	protected Long id;
	
	protected String name;
	
	public Subject() {}
	
	/**
	 * @param id
	 * @param name
	 */
	public Subject (Long id, String name) {
		this.setId(id);
		this.setName(name);
	}

	/**
	 * @return the subjectStudyList
	 */
	public List<SubjectStudy> getSubjectStudyList() {
		return subjectStudyList;
	}

	/**
	 * @param subjectStudyList the subjectStudyList to set
	 */
	public void setSubjectStudyList(List<SubjectStudy> subjectStudyList) {
		this.subjectStudyList = subjectStudyList;
	}
}
