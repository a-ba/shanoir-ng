import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { FormGroup, FormBuilder, Validators, FormControl } from '@angular/forms';
import { slideDown, preventInitialChildAnimations } from '../../../shared/animations/animations';
import { ImportBrukerService } from '../importBruker.service';

@Component({
    selector: 'import-bruker-file',
    templateUrl: 'importBrukerFile.component.html',
    styleUrls: ['../importBruker.component.css'],
    animations: [slideDown, preventInitialChildAnimations]
})

export class ImportBrukerFileComponent implements OnInit {
	@Input() enabled :boolean = true;
	@Input() active :boolean = true;
    public brukerImportFileForm: FormGroup;
    public tab_open: boolean = true;
    public archive: string;
    private extensionError: Boolean;
    private uploadError: String;
    fileToUpload: File = null;
    
    constructor(
    	private fb: FormBuilder,
    	private importBrukerService: ImportBrukerService,
    ) {}
    
    
	ngOnInit(): void {
		this.tab_open = this.active;
        this.buildForm();
	}
	
	buildForm(): void {
        this.brukerImportFileForm = this.fb.group({
            'fu': new FormControl(),
        });
    
        this.brukerImportFileForm.valueChanges
            .subscribe(data => this.onValueChanged(data));
        this.onValueChanged(); // (re)set validation messages now
    }
        
    onValueChanged(data?: any): void {
        if (!this.brukerImportFileForm) { return; }
        const form = this.brukerImportFileForm;
        for (const field in this.formErrors) {
                // clear previous error message (if any)
                this.formErrors[field] = '';
            const control = form.get(field);
            if (control && control.dirty && !control.valid) {
                for (const key in control.errors) {
                        this.formErrors[field] += key;
                }
            }
        }
    }
    
    formErrors = {
        'fu': '',
    };
    
    uploadArchive(event: any): void {
    	// checkExtension
    	this.extensionError = false;
    	let file:any = event.target.files;
        let index:any = file[0].name.lastIndexOf(".");
        let strsubstring: any = file[0].name.substring(index, file[0].name.length);
        if (strsubstring != '.zip') {
            this.extensionError = true;
            return;
        } 
        this.fileToUpload = file.item(0);
    	this.uploadError = '';
    	this.importBrukerService.postFile(this.fileToUpload)
        	.subscribe(res => {
            	console.log(res);
    			this.archive = this.fileToUpload.name;
                }, 
                (err: String) => {
                	console.log('error in posting File ' + err);
                	this.archive = '';
                	this.uploadError = err;
                }
            );
        
    }
    
}