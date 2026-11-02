import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal, OnInit, computed } from '@angular/core';

interface EquityPoint { pointIndex: number; realizedAt: string; equityUsdt: number; netPnlUsdt: number; }
interface ReplayHistory { summary: ReplayRow; equityCurve: EquityPoint[]; }
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
      <div class="paper-access" aria-label="Akses histori privat">
        <label for="paper-read-key">Kunci akses histori (hanya sesi ini)</label>
        <div class="paper-key-row">
          <input id="paper-read-key" type="password" autocomplete="off" [value]="readKey()"
            (input)="readKey.set($any($event.target).value)" placeholder="Masukkan kunci akses dari server" />
          <button type="button" (click)="refresh()" [disabled]="loading() || readKey().length < 32">Buka histori</button>
          <button type="button" (click)="clearAccess()">Hapus kunci</button>
        </div>
        <small>Histori dinonaktifkan secara default. Kunci tidak disimpan di localStorage. Jangan masukkan password PostgreSQL di sini.</small>
      </div>
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
          <p>Jika API histori belum diaktifkan secara aman di backend, data tetap tidak bisa dibuka. Ini tidak berarti market bermasalah.</p>
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
                  <span>{{ entry.completedAt | date:'medium' }}</span>
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
                <button type="button" (click)="loadCurve(entry.id)" [disabled]="curveLoading()">Lihat grafik modal</button>
                <small>Hari risiko: {{ entry.riskDayUtc }} (UTC) · ID: {{ entry.id }}</small>
              </article>
            }
          </div>
          @if (curveLoading()) {
            <div class="paper-empty" role="status">Memuat grafik dari PostgreSQL…</div>
          } @else if (curveError()) {
            <div class="paper-empty" role="alert">{{ curveError() }}</div>
          } @else if (selected()) {
            <section class="paper-chart" aria-label="Grafik modal simulasi tersimpan">
              <h3>Perkembangan modal simulasi</h3>
              <p>{{ selected()!.summary.provenance === 'SYNTHETIC_FIXTURE' ? 'Data sintetis untuk pengujian' : 'Replay historis' }} · Nilai USDT yang telah terealisasi</p>
              <div class="paper-values">
                <div><small>Modal awal</small><strong>{{ selected()!.summary.initialEquity | number:'1.2-2' }} USDT</strong></div>
                <div><small>Modal akhir</small><strong>{{ selected()!.summary.finalEquity | number:'1.2-2' }} USDT</strong></div>
              </div>
              @if (selected()!.equityCurve.length === 0) {
                <p>Tidak ada posisi yang selesai dalam simulasi ini.</p>
              } @else {
                <svg class="equity-chart" viewBox="0 0 600 220" preserveAspectRatio="none" role="img" aria-label="Grafik garis nilai modal simulasi">
                  <line x1="18" y1="190" x2="582" y2="190" stroke="#cbd5e1" stroke-width="1"/>
                  <polyline [attr.points]="chartPoints()" fill="none" stroke="#5742c6" stroke-width="3" stroke-linejoin="round" stroke-linecap="round"/>
                </svg>
                <p>Grafik berdasarkan transaksi selesai, bukan saldo exchange atau harga real-time.</p>
                <div class="paper-points">
                  @for (p of selected()!.equityCurve; track p.pointIndex) {
                    <div><time>{{ p.realizedAt | date:'short' }}</time><strong>{{ p.equityUsdt | number:'1.2-2' }} USDT</strong><span [class.loss]="p.netPnlUsdt < 0">{{ p.netPnlUsdt > 0 ? '+' : '' }}{{ p.netPnlUsdt | number:'1.2-2' }}</span></div>
                  }
                </div>
              }
            </section>
          }
        </section>
      }
    </section>
  `,
  styles: [`
    :host { display:block; min-width:0; color:#1e293b; }
    .paper-root { max-width:1300px; margin:0 auto; padding:24px 20px 90px; color:#1e293b; }
    .paper-heading { display:flex; justify-content:space-between; align-items:center; gap:16px; }
    .paper-heading h2 { margin:5px 0 10px; font-size:clamp(24px,3vw,32px); line-height:1.2; color:#17233c; font-weight:800; }
    .paper-heading p,.paper-results>p { color:#475569; line-height:1.6; margin:8px 0; }
    .paper-eyebrow { font-size:12px; font-weight:800; letter-spacing:.07em; color:#5b4cc4!important; }
    button { border:1px solid #b8b0ee; color:#4f46a5; background:#f4f2ff; border-radius:12px; padding:11px 16px; font-weight:750; cursor:pointer; white-space:nowrap; }
    button:hover { background:#e8e4ff; } button:focus-visible,summary:focus-visible { outline:3px solid #6c63ff; outline-offset:3px; }
    button:disabled { opacity:.65; cursor:wait; }
    .paper-access { border:1px solid #d9e1ee; background:#fff; border-radius:16px; padding:16px; margin:16px 0; display:grid; gap:9px; }
    .paper-access label { font-weight:750; }
    .paper-access small { color:#64748b; }
    .paper-key-row { display:flex; flex-wrap:wrap; gap:8px; }
    .paper-key-row input { min-width:160px; flex:1; border-radius:10px; border:1px solid #cbd5e1; padding:10px; color:#17233c; background:#fff; font:inherit; }
    .paper-chart { margin:24px 0; padding:18px; border:1px solid #d9e1ee; background:#fff; border-radius:16px; }
    .paper-chart p { color:#475569; }
    .equity-chart { display:block; width:100%; height:220px; background:#f8fafc; border-radius:12px; }
    .paper-points { max-height:230px; overflow:auto; }
    .paper-points div { display:grid; grid-template-columns:1.3fr 1fr auto; gap:8px; padding:9px 0; border-bottom:1px solid #e2e8f0; color:#334155; font-size:13px; }
    .paper-points span { color:#16885b; } .paper-points span.loss { color:#b42335; }
    .paper-notice,.paper-empty { border:1px solid #ced8e9; border-radius:16px; padding:19px; margin:20px 0; background:#ffffff; color:#334155; line-height:1.65; box-shadow:0 5px 17px rgba(30,45,77,.06); }
    .paper-notice { border-left:4px solid #6757d9; background:#f6f4ff; }
    .paper-notice strong,.paper-empty strong { display:block; margin-bottom:6px; color:#17233c; }
    .paper-empty p { color:#475569; }
    .paper-guide,.paper-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px; }
    .paper-guide article,.paper-card { border:1px solid #d9e1ee; background:#ffffff; padding:18px; border-radius:16px; min-width:0; color:#1e293b; box-shadow:0 5px 20px rgba(30,45,77,.05); }
    .paper-guide article:nth-child(1) { border-top:4px solid #16885b; }
    .paper-guide article:nth-child(2) { border-top:4px solid #bf7b12; }
    .paper-guide article:nth-child(3) { border-top:4px solid #c4424d; }
    .paper-guide article strong { color:#17233c; font-weight:800; }
    .paper-guide p { font-size:14px; color:#475569; line-height:1.65; margin:12px 0 0; }
    .paper-dot { display:inline-block; width:10px; height:10px; border-radius:50%; margin-right:9px; }
    .normal { background:#16885b; } .watch { background:#bf7b12; } .lock { background:#c4424d; }
    .paper-explain { margin:23px 0; padding:0 16px; background:#fff; border:1px solid #d9e1ee; border-radius:16px; }
    details { border-bottom:1px solid #e2e8f0; padding:16px 0; } details:last-child { border-bottom:0; }
    summary { cursor:pointer; font-weight:750; color:#1e293b; }
    details p { color:#475569; line-height:1.7; padding:0 4px; }
    .paper-results h3 { color:#17233c; }
    .paper-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }
    .paper-card-title { display:flex; flex-wrap:wrap; justify-content:space-between; gap:8px; }
    .paper-card-title span,.paper-card>small { color:#64748b; font-size:12px; overflow-wrap:anywhere; }
    .paper-status { color:#9a650a; font-weight:800; } .paper-locked { color:#b42335; }
    .paper-values { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14px; margin:18px 0; }
    .paper-values div { display:grid; gap:5px; } .paper-values small { color:#64748b; } .paper-values strong { color:#17233c; overflow-wrap:anywhere; }
    @media(max-width:760px) {
      .paper-root { padding:16px 12px 85px; }
      .paper-heading { align-items:flex-start; flex-direction:column; gap:10px; }
      .paper-heading button { font-size:14px; padding:10px 14px; }
      .paper-guide,.paper-grid { grid-template-columns:1fr; }
      .paper-guide article { padding:16px; }
    }
  `]
})
export class PaperRiskComponent implements OnInit {
  ngOnInit(): void { this.refresh(); }
  private readonly http = inject(HttpClient);
  readonly rows = signal<ReplayRow[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly readKey = signal('');
  readonly selected = signal<ReplayHistory | null>(null);
  readonly curveLoading = signal(false);
  readonly curveError = signal('');
  readonly chartPoints = computed(() => {
    const history = this.selected();
    if (!history || !history.equityCurve.length) return '';
    const values = [history.summary.initialEquity, ...history.equityCurve.map(p => p.equityUsdt)];
    const min = Math.min(...values), max = Math.max(...values);
    const scale = Math.max(max - min, 0.000001);
    return values.map((value, i) => `${18 + (564 * i / Math.max(values.length - 1, 1))},${190 - (160 * (value - min) / scale)}`).join(' ');
  });
  private requestNumber = 0;

  clearAccess(): void {
    this.readKey.set('');
    this.rows.set([]);
    this.selected.set(null);
    this.curveError.set('');
    this.error.set('');
    this.requestNumber++;
    this.loading.set(false);
  }

  loadCurve(id: string): void {
    if (this.readKey().length < 32) return;
    this.curveLoading.set(true);
    this.curveError.set('');
    this.selected.set(null);
    this.http.get<ReplayHistory>(`/api/v1/dev/futures-paper/archive/${encodeURIComponent(id)}/equity`, {
      headers: { 'X-CoinMind-Archive-Key': this.readKey() }
    }).subscribe({
      next: result => { this.selected.set(result); this.curveLoading.set(false); },
      error: () => { this.curveError.set('Grafik tidak dapat dibaca. Periksa akses dan kelengkapan data simulasi.'); this.curveLoading.set(false); }
    });
  }

  refresh(): void {
    const current = ++this.requestNumber;
    if (this.readKey().length < 32) {
      this.error.set('Masukkan kunci baca histori minimal 32 karakter terlebih dahulu.');
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.http.get<ReplayRow[]>('/api/v1/dev/futures-paper/archive?limit=20', {
      headers: { 'X-CoinMind-Archive-Key': this.readKey() }
    }).subscribe({
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
