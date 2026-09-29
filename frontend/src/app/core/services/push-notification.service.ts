import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { PushPublicKeyResponse } from '../models/market.models';

export type PushDeviceStatus =
  | 'enabled'
  | 'permission-required'
  | 'install-required'
  | 'unsupported'
  | 'disabled'
  | 'denied';

@Injectable({ providedIn: 'root' })
export class PushNotificationService {
  private readonly http = inject(HttpClient);

  async initialize(): Promise<PushDeviceStatus> {
    if (!this.isSupported()) {
      return 'unsupported';
    }

    const config = await this.getConfig();
    if (!config.enabled || !config.publicKey) {
      return 'disabled';
    }

    await navigator.serviceWorker.register('/service-worker.js');
    const registration = await navigator.serviceWorker.ready;

    if (Notification.permission === 'denied') {
      return 'denied';
    }

    if (Notification.permission === 'granted') {
      await this.ensureSubscription(registration, config.publicKey);
      return 'enabled';
    }

    return this.isInstalledPwa()
      ? 'permission-required'
      : 'install-required';
  }

  async enable(): Promise<PushDeviceStatus> {
    if (!this.isSupported()) {
      return 'unsupported';
    }

    if (!this.isInstalledPwa()) {
      return 'install-required';
    }

    const config = await this.getConfig();
    if (!config.enabled || !config.publicKey) {
      return 'disabled';
    }

    const permission = await Notification.requestPermission();
    if (permission !== 'granted') {
      return 'denied';
    }

    const registration = await navigator.serviceWorker.register('/service-worker.js');
    await navigator.serviceWorker.ready;
    await this.ensureSubscription(registration, config.publicKey);

    return 'enabled';
  }

  isInstalledPwa(): boolean {
    const standaloneDisplay = window.matchMedia('(display-mode: standalone)').matches;
    const iosStandalone = (navigator as Navigator & { standalone?: boolean }).standalone === true;
    return standaloneDisplay || iosStandalone;
  }

  private isSupported(): boolean {
    return (
      'serviceWorker' in navigator &&
      'PushManager' in window &&
      'Notification' in window
    );
  }

  private async getConfig(): Promise<PushPublicKeyResponse> {
    return firstValueFrom(
      this.http.get<PushPublicKeyResponse>('/api/v1/push/public-key')
    );
  }

  private async ensureSubscription(
    registration: ServiceWorkerRegistration,
    publicKey: string
  ): Promise<void> {
    let subscription = await registration.pushManager.getSubscription();

    if (!subscription) {
      subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: this.urlBase64ToArrayBuffer(publicKey)
      });
    }

    await firstValueFrom(
      this.http.post('/api/v1/push/subscribe', subscription.toJSON())
    );
  }

  private urlBase64ToArrayBuffer(base64String: string): ArrayBuffer {
    const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
    const base64 = (base64String + padding)
      .replace(/-/g, '+')
      .replace(/_/g, '/');

    const rawData = atob(base64);
    const bytes = new Uint8Array(new ArrayBuffer(rawData.length));

    for (let i = 0; i < rawData.length; ++i) {
      bytes[i] = rawData.charCodeAt(i);
    }

    return bytes.buffer;
  }
}
