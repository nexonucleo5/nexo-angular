import { ApplicationConfig, inject, provideAppInitializer } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { errorInterceptor } from './core/error.interceptor';
import { AuthService } from './services/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    // O access token mora só na memória, então um F5 o perde. A renovação silenciosa
    // roda aqui, antes da primeira rota: assim a tela inicial já sai com Authorization
    // em vez de tomar 401 e depender do retry do interceptor. O arranque espera esta
    // promise, e ela nunca rejeita — falha de renovação apenas começa deslogado.
    provideAppInitializer(() => inject(AuthService).restaurarSessao()),
  ],
};
