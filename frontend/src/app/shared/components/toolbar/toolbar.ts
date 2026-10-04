import { Component } from '@angular/core';
import { AppIconComponent } from '../icon/icon';

@Component({
  selector: 'app-toolbar',
  standalone: true,
  imports: [AppIconComponent],
  templateUrl: './toolbar.html',
})
export class ToolbarComponent {}
