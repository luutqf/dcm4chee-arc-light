import { Component } from '@angular/core';
import {MatDialogRef} from "@angular/material/dialog";
//import { MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';

@Component({
    selector: 'app-remove-part-selector',
    templateUrl: './remove-part-selector.component.html',
    standalone: false
})
export class RemovePartSelectorComponent{
    selectedOption;
    private _toRemoveElement;

    constructor(public dialogRef: MatDialogRef<RemovePartSelectorComponent>) { }

    get toRemoveElement() {
        return this._toRemoveElement;
    }

    set toRemoveElement(value) {
        this._toRemoveElement = value;
    }
}
