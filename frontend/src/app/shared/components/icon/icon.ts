import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { LucideDynamicIcon } from '@lucide/angular';
import type { AppIconName } from './icon.types';
import { ICON_MAP } from './icon.map';

/**
 * Centralized icon component for the application.
 *
 * Usage:
 *   <app-icon name="calendar" />
 *   <app-icon name="bell" [size]="20" class="text-gray-500" />
 *
 * Feature and page components must use this component instead of importing
 * Lucide icons directly.
 */
@Component({
  selector: 'app-icon',
  standalone: true,
  imports: [LucideDynamicIcon],
  templateUrl: './icon.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppIconComponent {
  readonly name = input.required<AppIconName>();
  readonly size = input<number>(18);
  readonly cssClass = input('', { alias: 'class' });

  protected readonly lucideIcon = computed(() => ICON_MAP[this.name()]);
}
