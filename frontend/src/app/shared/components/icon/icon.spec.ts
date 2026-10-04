import { describe, it, expect } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { AppIconComponent } from './icon';
import type { AppIconName } from './icon.types';

/** Host component used to drive inputs in tests */
@Component({
  standalone: true,
  imports: [AppIconComponent],
  template: `<app-icon [name]="name" [size]="size" class="text-gray-500" />`,
})
class HostComponent {
  name: AppIconName = 'calendar';
  size = 18;
}

function createHost(name: AppIconName, size = 18) {
  TestBed.configureTestingModule({ imports: [HostComponent] });
  const fixture = TestBed.createComponent(HostComponent);
  fixture.componentInstance.name = name;
  fixture.componentInstance.size = size;
  fixture.detectChanges();
  return fixture;
}

describe('AppIconComponent', () => {
  it('should render an svg element for a Lucide icon', () => {
    const fixture = createHost('calendar');
    const svg = fixture.nativeElement.querySelector('svg');
    expect(svg).not.toBeNull();
  });

  it('should render an svg element for a custom icon', () => {
    const fixture = createHost('dashboard');
    const svg = fixture.nativeElement.querySelector('svg');
    expect(svg).not.toBeNull();
  });

  it('should pass the size attribute to custom SVGs', () => {
    const fixture = createHost('dashboard', 24);
    const svg: SVGElement = fixture.nativeElement.querySelector('svg');
    expect(svg.getAttribute('width')).toBe('24');
    expect(svg.getAttribute('height')).toBe('24');
  });

  it('should pass CSS classes to the rendered SVG', () => {
    const fixture = createHost('calendar');
    const svg: SVGElement = fixture.nativeElement.querySelector('svg');
    expect(svg.classList.contains('text-gray-500')).toBe(true);
  });

  const lucideIcons: AppIconName[] = [
    'calendar',
    'bell',
    'search',
    'chevron-left',
    'chevron-right',
    'plus',
    'mail',
    'lock',
    'eye',
    'eye-off',
    'user',
    'alert-circle',
    'check-circle',
    'menu',
    'sparkles',
    'zap',
    'loader-circle',
  ];

  lucideIcons.forEach((name) => {
    it(`should render svg for Lucide icon: ${name}`, () => {
      const fixture = createHost(name);
      const svg = fixture.nativeElement.querySelector('svg');
      expect(svg).not.toBeNull();
    });
  });

  const customIcons: AppIconName[] = [
    'dashboard',
    'upcoming',
    'history',
    'settings',
    'study-planner',
    'sharing',
  ];

  customIcons.forEach((name) => {
    it(`should render svg for custom icon: ${name}`, () => {
      const fixture = createHost(name);
      const svg = fixture.nativeElement.querySelector('svg');
      expect(svg).not.toBeNull();
    });
  });
});
