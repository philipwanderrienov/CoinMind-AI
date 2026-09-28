import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { PushPublicKeyResponse } from '../models/market.models';

@Injectable({ providedIn: 'root' })
export class PushNotificationService {
  private readonly http = inject(HttpClient);

  async enable(): Promise<'enabled' | 'unsupported' | 'disabled' | 'denied'> {
    if (
      !('serviceWorker' in navigator) ||
      !('PushManager' in window) ||
      !('Notification' in window)
    ) {
      return 'unsupported';
    }

    const config = await firstValueFrom(
      this.http.get<PushPublicKeyResponse>('/api/v1/push/public-key')
    );

    if (!config.enabled || !config.publicKey) {
      return 'disabled';
    }

    const permission = await Notification.requestPermission();
    if (permission !== 'granted') {
      return 'denied';
    }

    const registration = await navigator.serviceWorker.register('/service-worker.js');
    await navigator.serviceWorker.ready;

    let subscription = await registration.pushManager.getSubscription();

    if (!subscription) {
      subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: this.urlBase64ToArrayBuffer(config.publicKey)
      });
    }

    await firstValueFrom(
      this.http.post('/api/v1/push/subscribe', subscription.toJSON())
    );

    return 'enabled';
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
