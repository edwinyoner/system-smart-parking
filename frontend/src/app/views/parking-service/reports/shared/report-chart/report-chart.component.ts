// src/app/views/parking-service/reports/shared/report-chart/report-chart.component.ts
import {
  AfterViewInit, Component, ElementRef, Input, OnChanges, OnDestroy, ViewChild
} from '@angular/core';
import { Chart, ChartConfiguration, ChartDataset } from 'chart.js/auto';
import { chartInk, seriesColors } from '../../../../../core/utils/report.util';

/** Una serie del gráfico. reference = línea punteada gris (por ejemplo, la capacidad) */
export interface ChartSeries {
  name: string;
  values: number[];
  reference?: boolean;
}

/**
 * Gráfico de barras de los reportes (una sola escala). Colores en orden fijo y validados,
 * leyenda solo con 2 o más series, tooltip al pasar el mouse, y se redibuja al cambiar de tema.
 * Los datos también están en una tabla de la misma vista.
 */
@Component({
  selector: 'app-report-chart',
  standalone: true,
  template: `<div class="chart-box" [style.height.px]="height"><canvas #canvas role="img" [attr.aria-label]="label"></canvas></div>`,
  styles: [`.chart-box { position: relative; width: 100%; }`]
})
export class ReportChartComponent implements AfterViewInit, OnChanges, OnDestroy {

  @ViewChild('canvas') canvas!: ElementRef<HTMLCanvasElement>;

  @Input({ required: true }) labels: string[] = [];
  @Input({ required: true }) series: ChartSeries[] = [];
  @Input() label = 'Gráfico';
  @Input() height = 280;
  /** Barras horizontales (para nombres largos, como parqueo y zona) */
  @Input() horizontal = false;
  /** Formato de los valores en el eje y el tooltip */
  @Input() format: (value: number) => string = v => String(v);

  private chart: Chart | null = null;
  private observer: MutationObserver | null = null;

  ngAfterViewInit(): void {
    this.render();
    // Al cambiar el tema (claro/oscuro) se vuelven a leer los colores
    this.observer = new MutationObserver(() => this.render());
    for (const el of [document.documentElement, document.body]) {
      this.observer.observe(el, { attributes: true, attributeFilter: ['data-coreui-theme'] });
    }
  }

  ngOnChanges(): void {
    if (!this.chart) return;
    // Mismos datos de forma: se actualiza sin animación (refresco en vivo sin parpadeo)
    if (this.chart.data.datasets.length === this.series.length) {
      this.chart.data.labels = this.labels;
      this.series.forEach((s, i) => (this.chart!.data.datasets[i].data = s.values));
      this.chart.update('none');
    } else {
      this.render();
    }
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
    this.chart?.destroy();
  }

  private render(): void {
    if (!this.canvas) return;
    this.chart?.destroy();
    const ink = chartInk();
    const colors = seriesColors();
    let slot = 0;

    const datasets: ChartDataset<'bar' | 'line'>[] = this.series.map(s => {
      if (s.reference) {
        return {
          type: 'line', label: s.name, data: s.values, borderColor: ink.muted, borderWidth: 2,
          borderDash: [6, 4], pointRadius: 0, pointHitRadius: 8, fill: false, order: 0
        } as ChartDataset<'line'>;
      }
      const color = colors[slot++ % colors.length];
      return {
        type: 'bar', label: s.name, data: s.values, backgroundColor: color, hoverBackgroundColor: color,
        borderRadius: 4, borderSkipped: 'start', borderColor: ink.surface, borderWidth: 1,
        maxBarThickness: 28, order: 1
      } as ChartDataset<'bar'>;
    });

    const valueAxis = {
      beginAtZero: true,
      grid: { color: ink.grid },
      border: { display: false },
      ticks: { color: ink.muted, precision: 0, callback: (v: string | number) => this.format(Number(v)) }
    };
    const categoryAxis = {
      grid: { display: false },
      border: { color: ink.grid },
      ticks: { color: ink.muted, autoSkip: !this.horizontal, maxRotation: 0 }
    };

    const config: ChartConfiguration<'bar' | 'line'> = {
      type: 'bar',
      data: { labels: this.labels, datasets },
      options: {
        indexAxis: this.horizontal ? 'y' : 'x',
        responsive: true,
        maintainAspectRatio: false,
        animation: { duration: 300 },
        interaction: { mode: 'index', intersect: false },
        scales: this.horizontal ? { x: valueAxis, y: categoryAxis } : { x: categoryAxis, y: valueAxis },
        plugins: {
          legend: {
            display: this.series.length > 1,
            position: 'top',
            align: 'end',
            labels: { color: ink.text, usePointStyle: true, boxWidth: 8 }
          },
          tooltip: {
            callbacks: { label: c => ` ${c.dataset.label}: ${this.format(Number(c.parsed[this.horizontal ? 'x' : 'y']))}` }
          }
        }
      }
    };
    this.chart = new Chart(this.canvas.nativeElement, config);
  }
}
