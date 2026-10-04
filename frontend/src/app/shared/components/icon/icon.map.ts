import {
  LucideCalendar,
  LucideBell,
  LucideSearch,
  LucideChevronLeft,
  LucideChevronRight,
  LucidePlus,
  LucideMail,
  LucideLock,
  LucideEye,
  LucideEyeOff,
  LucideUser,
  LucideCircleAlert,
  LucideCircleCheck,
  LucideMenu,
  LucideSparkles,
  LucideZap,
  LucideLoaderCircle,
} from '@lucide/angular';
import type { LucideIconData } from '@lucide/angular';
import type { AppIconName } from './icon.types';

/**
 * Maps each AppIconName to its corresponding Lucide icon data.
 * Icons without a Lucide equivalent are set to `null` and rendered
 * as custom inline SVGs inside AppIconComponent.
 */
export const ICON_MAP = {
  // Lucide icons
  calendar: LucideCalendar.icon,
  bell: LucideBell.icon,
  search: LucideSearch.icon,
  'chevron-left': LucideChevronLeft.icon,
  'chevron-right': LucideChevronRight.icon,
  plus: LucidePlus.icon,
  mail: LucideMail.icon,
  lock: LucideLock.icon,
  eye: LucideEye.icon,
  'eye-off': LucideEyeOff.icon,
  user: LucideUser.icon,
  'alert-circle': LucideCircleAlert.icon,
  'check-circle': LucideCircleCheck.icon,
  menu: LucideMenu.icon,
  sparkles: LucideSparkles.icon,
  zap: LucideZap.icon,
  'loader-circle': LucideLoaderCircle.icon,
  // Custom SVG icons (null = rendered inline in AppIconComponent)
  dashboard: null,
  upcoming: null,
  history: null,
  settings: null,
  'study-planner': null,
  sharing: null,
} satisfies Record<AppIconName, LucideIconData | null>;

export type AppLucideIcon = Exclude<(typeof ICON_MAP)[keyof typeof ICON_MAP], null>;
