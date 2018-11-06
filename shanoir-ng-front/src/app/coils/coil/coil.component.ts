import { Component, Input } from '@angular/core';
import { FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

import { AcquisitionEquipment } from '../../acquisition-equipments/shared/acquisition-equipment.model';
import { ManufacturerModel } from '../../acquisition-equipments/shared/manufacturer-model.model';
import { Step } from '../../breadcrumbs/breadcrumbs.service';
import { Center } from '../../centers/shared/center.model';
import { CenterService } from '../../centers/shared/center.service';
import { EntityComponent } from '../../shared/components/entity/entity.component.abstract';
import { CoilType } from '../shared/coil-type.enum';
import { Coil } from '../shared/coil.model';
import { CoilService } from '../shared/coil.service';

@Component({
    selector: 'coil',
    templateUrl: 'coil.component.html',
    styleUrls: ['coil.component.css']
})
export class CoilComponent extends EntityComponent<Coil> {
   
    @Input() acqEquip: AcquisitionEquipment;
    private centers: Center[] = [];
    private manufModels: ManufacturerModel[] = [];
    private coilTypes: CoilType[] = CoilType.all();
    private prefilledCenter: Center;
    private prefilledManuf: ManufacturerModel;

    constructor(
            private route: ActivatedRoute,
            private coilService: CoilService, 
            private centerService: CenterService) {
        super(route, 'coil');
    }

    get coil(): Coil { return this.entity; }
    set coil(coil: Coil) { this.entity = coil; }

    initView(): Promise<void> {
        return this.coilService.get(this.id).then(coil => {
            this.coil = coil;
        });
    }

    initEdit(): Promise<void> {
        return Promise.all([
            this.centerService.getAll(),
            this.coilService.get(this.id)
        ]).then(([centers, coil]) => {
            this.centers = centers;
            this.coil = coil;
            if (this.acqEquip) {
                coil.center = this.acqEquip.center;
                coil.manufacturerModel = this.acqEquip.manufacturerModel;
            }
            this.coil.center = this.centers.find(center => center.id == this.coil.center.id);
            this.updateManufList(this.coil.center);
            this.coil.manufacturerModel = this.manufModels.find(manuf => manuf.id == this.entity.manufacturerModel.id);
        });
    }

    initCreate(): Promise<void> {
        this.entity = new Coil();
        this.prefilledCenter = this.breadcrumbsService.currentStep.getPrefilledValue('center');
        if (this.prefilledCenter) {
            this.coil.center = this.prefilledCenter;
            this.centers = [this.prefilledCenter];
        } else {
            this.centerService.getAll().then(centers => {
                this.centers = centers;
            });
        }
        this.prefilledManuf = this.breadcrumbsService.currentStep.getPrefilledValue('manufacturerModel');
        if (this.prefilledManuf) {
            this.coil.manufacturerModel = this.prefilledManuf;
            this.manufModels = [this.prefilledManuf];
        }
        return Promise.resolve();
    }

    buildForm(): FormGroup {
        return this.formBuilder.group({
            'name': [this.coil.name, [Validators.required, Validators.minLength(2)]],
            'acquiEquipModel': [{value: this.coil.manufacturerModel, disabled: this.prefilledManuf}, [Validators.required]],
            'center': [{value: this.coil.center, disabled: this.prefilledCenter}, [Validators.required]],
            'coilType': [this.coil.coilType],
            'nbChannel': [this.coil.numberOfChannels],
            'serialNb': [this.coil.serialNumber]
        });
    }

    private updateManufList(center: Center): void {
        this.manufModels = [];
        if (center && center.acquisitionEquipments) {
            for (let acqEqu of center.acquisitionEquipments) {
                this.manufModels.push(acqEqu.manufacturerModel);
            }
        }
    }

    private openNewCenter() {
        let currentStep: Step = this.breadcrumbsService.currentStep;
        this.router.navigate(['/center/create']).then(success => {
            currentStep.waitFor(this.breadcrumbsService.currentStep).subscribe(entity => {
                (currentStep.entity as Coil).center = entity as Center;
            });
        });
    }

    private openNewManufModel() {
        let currentStep: Step = this.breadcrumbsService.currentStep;
        this.router.navigate(['/manufacturer-model/create']).then(success => {
            currentStep.waitFor(this.breadcrumbsService.currentStep).subscribe(entity => {
                (currentStep.entity as Coil).manufacturerModel = entity as ManufacturerModel;
            });
        });
    }

}