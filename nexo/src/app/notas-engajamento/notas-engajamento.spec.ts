import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NotasEngajamento } from './notas-engajamento';

describe('NotasEngajamento', () => {
  let component: NotasEngajamento;
  let fixture: ComponentFixture<NotasEngajamento>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotasEngajamento],
    }).compileComponents();

    fixture = TestBed.createComponent(NotasEngajamento);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
