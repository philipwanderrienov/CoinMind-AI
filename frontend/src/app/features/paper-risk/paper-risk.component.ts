import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';

interface ReplayRow {
  id: string;
  completedAt: string;
  provenance: 'SYNTHETIC_FIXTURE' | 'HISTORICAL_REPLAY';
  initialEquity: number;
  finalEquity: number;
  maxDrawdownQuote: number;
  executed: number;
  riskStatus: 'NORMAL' | 'LOSS_MONITORING' | 'DAILY_RISK_LOCK';
  riskDayUtc: string;
  dailyLossLimitQuote: number;
  realizedLossQuote: number;
  remainingLossBudgetQuote: number;
}

@Component({
  selector: 'app-paper-risk',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="paper-root" aria-labelledby="paper-title">
      <header class="paper-heading">
        <div>
          <p class="paper-eyebrow">SIMULASI • BUKAN AKUN EXCHANGE</p>
          <h2 id="paper-title">Simulasi & Risiko</h2>
          <p>Pahami hasil percobaan strategi, perkembangan modal simulasi, dan alasan sistem menahan entry.</p>
        </div>
        <button type="button" (click)="refresh()" [disabled]="loading()">Muat ulang</button>
      </header>
      <div class="paper-notice" role="note">
        <strong>Ini bukan saldo asli atau sinyal trading.</strong>
        Hasil berasal dari uji historis atau data sintetis. CoinMind tidak membuka atau menutup posisi otomatis.
      </div>

      <div class="paper-guide" aria-label="Panduan status risiko">
        <article><span class="paper-dot normal"></span><strong>NORMAL</strong><p>Belum ada kerugian bersih pada hari risiko. Filter strategi lainnya tetap berlaku.</p></article>
        <article><span class="paper-dot watch"></span><strong>WASPADA RUGI</strong><p>Sudah rugi, tetapi batas harian belum tercapai. Ukuran posisi berikutnya mengikuti modal simulasi.</p></article>
        <article><span class="paper-dot lock"></span><strong>ENTRY DITAHAN</strong><p>Batas rugi harian tercapai. Analisis tetap berjalan, tetapi entry simulasi baru tidak diizinkan.</p></article>
      </div>

      <div class="paper-explain">
        <details><summary>Equity itu apa?</summary><p>Equity adalah modal simulasi setelah keuntungan atau kerugian yang sudah terealisasi. Bukan saldo akun exchange Anda.</p></details>
        <details><summary>Drawdown itu apa?</summary><p>Penurunan modal dari titik tertinggi yang pernah dicapai dalam simulasi. Semakin besar, semakin berat penurunan modalnya.</p></details>
        <details><summary>Kapan batas rugi harian direset?</summary><p>Perhitungan saat ini memakai pergantian hari UTC (07.00 WIB). Reset bukan jaminan strategi langsung aman untuk entry.</p></details>
      </div>

      @if (loading()) {
        <div class="paper-empty" role="status">Sedang mengambil histori simulasi…</div>
      } @else if (error()) {
        <div class="paper-empty" role="status">
          <strong>Histori simulasi belum bisa diakses.</strong>
          <p>{{ error() }}</p>
          <p>API histori dinonaktifkan secara default demi keamanan. Ini tidak berarti saldo atau market sedang bermasalah.</p>
        </div>
      } @else if (rows().length === 0) {
        <div class="paper-empty" role="status">
          <strong>Belum ada hasil simulasi tersimpan.</strong>
          <p>Jalankan dan simpan replay dari backend terlebih dahulu. Kami tidak menampilkan angka contoh sebagai hasil nyata.</p>
        </div>
      } @else {
        <section class="paper-results" aria-label="Histori hasil backtest">
          <h3>Riwayat simulasi tersimpan</h3>
          <p>Urutan terbaru; nilai dalam USDT. Label risiko mencerminkan waktu replay disimpan, bukan akun Anda saat ini.</p>
          <div class="paper-grid">
            @for (entry of rows(); track entry.id) {
              <article class="paper-card">
                <div class="paper-card-title">
                  <strong>{{ entry.provenance === 'SYNTHETIC_FIXTURE' ? 'Data buatan untuk pengujian' : 'Replay historis' }}</strong>
                  <span>{{ entry.completedAt | date:'medium':'':'id' }}</span>
                </div>
                <p class="paper-status" [class.paper-locked]="entry.riskStatus === 'DAILY_RISK_LOCK'">
                  {{ label(entry.riskStatus) }}
                </p>
                <div class="paper-values">
                  <div><small>Modal awal</small><strong>{{ entry.initialEquity | number:'1.2-2' }} USDT</strong></div>
                  <div><small>Modal akhir</small><strong>{{ entry.finalEquity | number:'1.2-2' }} USDT</strong></div>
                  <div><small>Penurunan terbesar</small><strong>{{ entry.maxDrawdownQuote | number:'1.2-2' }} USDT</strong></div>
                  <div><small>Posisi selesai</small><strong>{{ entry.executed }}</strong></div>
                  <div><small>Kerugian harian</small><strong>{{ entry.realizedLossQuote | number:'1.2-2' }} USDT</strong></div>
                  <div><small>Sisa batas rugi</small><strong>{{ entry.remainingLossBudgetQuote | number:'1.2-2' }} USDT</strong></div>
                </div>
                <small>Hari risiko: {{ entry.riskDayUtc }} (UTC) · ID: {{ entry.id }}</small>
              </article>
            }
          </div>
        </section>
      }
    </section>
  `,
  styles: [`
    :host { display:block; min-width:0; }
    .paper-root { padding:20px; max-width:1300px; margin:0 auto; color:var(--text-primary,#e8eef9); }
    .paper-heading { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; }
    .paper-heading h2 { margin:4px 0; font-size:clamp(23px,3vw,32px); }
    .paper-heading p, .paper-results>p { color:var(--text-secondary,#a9b7cd); line-height:1.6; }
    .paper-eyebrow { font-size:12px; font-weight:700; letter-spacing:.08em; color:#77b6fc!important; }
    button { border:1px solid #6e8bb4; color:inherit; background:transparent; border-radius:12px; padding:11px 14px; cursor:pointer; white-space:nowrap; }
    button:disabled { opacity:.5; cursor:wait; }
    .paper-notice,.paper-empty { border:1px solid #50607b; border-radius:14px; padding:18px; margin:20px 0; background:rgba(86,119,156,.10); line-height:1.6; }
    .paper-notice strong { display:block; margin-bottom:5px; }
    .paper-guide,.paper-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; }
    .paper-guide article,.paper-card { border:1px solid #45536e; background:rgba(96,116,147,.08); padding:17px; border-radius:14px; min-width:0; }
    .paper-guide p { font-size:13px; color:var(--text-secondary,#bbc8dc); line-height:1.55; }
    .paper-dot { display:inline-block; width:9px; height:9px; border-radius:50%; margin-right:9px; }
    .normal { background:#41c58a; } .watch { background:#e8b850; } .lock { background:#ef7474; }
    .paper-explain { margin:20px 0; }
    details { border-bottom:1px solid #45536e; padding:12px 0; }
    summary { cursor:pointer; font-weight:600; } details p { color:var(--text-secondary,#bbc8dc); line-height:1.6; }
    .paper-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }
    .paper-card-title { display:flex; flex-wrap:wrap; justify-content:space-between; gap:8px; }
    .paper-card-title span,.paper-card>small { color:var(--text-secondary,#bbc8dc); font-size:12px; overflow-wrap:anywhere; }
    .paper-status { color:#e8b850; font-weight:700; } .paper-locked { color:#f18d8d; }
    .paper-values { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14px; margin:18px 0; }
    .paper-values div { display:grid; gap:4px; } .paper-values small { color:var(--text-secondary,#bbc8dc); }
    @media(max-width:760px) { .paper-root { padding:12px; } .paper-guide,.paper-grid { grid-template-columns:1fr; } .paper-heading { align-items:center; } .paper-heading button { font-size:12px; padding:9px; } }
  `]
})
export class PaperRiskComponent {
  private readonly http = inject(HttpClient);
  readonly rows = signal<ReplayRow[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  private requestNumber = 0;

  refresh(): void {
    const current = ++this.requestNumber;
    this.loading.set(true);
    this.error.set('');
    this.http.get<ReplayRow[]>('/api/v1/dev/futures-paper/archive?limit=20').subscribe({
      next: entries => {
        if (current !== this.requestNumber) return;
        this.rows.set(Array.isArray(entries) ? entries : []);
        this.loading.set(false);
      },
      error: (failure: HttpErrorResponse) => {
        if (current !== this.requestNumber) return;
        this.rows.set([]);
        this.error.set(failure.status === 404
          ? 'Fitur histori belum diaktifkan di backend.'
          : 'Data belum tersedia atau server tidak dapat dihubungi.');
        this.loading.set(false);
      }
    });
  }

  label(value: ReplayRow['riskStatus']): string {
    switch (value) {
      case 'NORMAL': return 'NORMAL • Tidak ada rugi bersih harian';
      case 'LOSS_MONITORING': return 'WASPADA RUGI • Masih di bawah batas';
      case 'DAILY_RISK_LOCK': return 'ENTRY DITAHAN • Batas rugi tercapai';
      default: return 'STATUS BELUM DIKETAHUI';
    }
  }
}
