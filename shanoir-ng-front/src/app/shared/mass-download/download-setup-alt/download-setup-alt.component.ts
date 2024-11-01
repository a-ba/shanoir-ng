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

import { Component, ElementRef, EventEmitter, HostListener, Input, OnInit, Output, ViewChild } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Format } from 'src/app/datasets/shared/dataset.service';
import { GlobalService } from '../../services/global.service';
import { Option } from '../../select/select.component';
import { DownloadSetupOptions } from '../download-setup/download-setup.component';

@Component({
    selector: 'download-setup-alt',
    templateUrl: 'download-setup-alt.component.html',
    styleUrls: ['download-setup-alt.component.css']
})

export class DownloadSetupAltComponent implements OnInit {

    @Output() go: EventEmitter<Format> = new EventEmitter();
    @Output() close: EventEmitter<void> = new EventEmitter();
    form: UntypedFormGroup;
    @ViewChild('window') window: ElementRef;
    @Input() format: Format; 
    @Input() compatibilityMessage: boolean = true; 

    @Input() options: DownloadSetupOptions = new DownloadSetupOptions();
    formatOptions: Option<Format>[] = [
        new Option<Format>('dcm', 'Dicom', null, null, null, !this.options.hasDicom),
        new Option<Format>('nii', 'Nifti', null, null, null, !this.options.hasDicom),
        new Option<Format>('eeg', 'EEG', null, null, null, !this.options.hasEeg),
        new Option<Format>('BIDS', 'BIDS', null, null, null, !this.options.hasBids),
    ];
        
    constructor(private formBuilder: UntypedFormBuilder, globalService: GlobalService) {
        globalService.onNavigate.subscribe(() => {
            this.cancel();
        });
    }
    
    ngOnInit(): void {
        this.form = this.buildForm();
    }

    private buildForm(): UntypedFormGroup {
        let formGroup = this.formBuilder.group({
            'format': [{value: this.format || 'dcm', disabled: this.format}, [Validators.required]],
        });
        return formGroup;
    }

    downloadNow() {
        this.go.emit(this.form.get('format').value);
    }
    
    cancel() {
        this.close.emit();
    }

    @HostListener('click', ['$event'])
    onClick(clickEvent) {
        if (!this.window.nativeElement.contains(clickEvent.target)) {
            this.cancel();
        }
    }
}