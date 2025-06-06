import { Component } from '@angular/core';
import {MatDialogRef} from "@angular/material/dialog";
//import { MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';

@Component({
    selector: 'app-delete-rejected-instances',
    templateUrl: './delete-rejected-instances.component.html',
    styles: [`        
    `],
    standalone: false
})
export class DeleteRejectedInstancesComponent{
    private _rjnotes;
    private _results: any;
    constructor(public dialogRef: MatDialogRef<DeleteRejectedInstancesComponent>) { }

    get rjnotes() {
        return this._rjnotes;
    }

    set rjnotes(value) {
        this._rjnotes = value;
    }

    get results() {
        return this._results;
    }

    set results(value) {
        this._results = value;
    }
}
