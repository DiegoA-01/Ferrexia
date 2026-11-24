import { Directive, ElementRef, Renderer2, forwardRef, inject, input } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Campo de cifra (spec 017): reemplaza a {@code <input type="number">}. Es un campo de texto con teclado numérico en
 * celular, sin flechas (la rueda del mouse no cambia nada), que pone el punto de miles mientras se escribe y acepta la
 * coma decimal ("1.250.000", "2,5"). Al formulario le entrega un {@code number}, o {@code null} si está vacío, igual que
 * el campo de número de antes: el backend recibe el número limpio.
 *
 * Uso: {@code <input scCifra [decimales]="0" [ngModel]="x" (ngModelChange)="...">}.
 */
@Directive({
  selector: 'input[scCifra]',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => Cifra), multi: true }],
  host: {
    type: 'text',
    inputmode: 'decimal',
    autocomplete: 'off',
    '(input)': 'alEscribir()',
    '(blur)': 'alSalir()',
  },
})
export class Cifra implements ControlValueAccessor {
  /** Decimales que se aceptan después de la coma (0 = solo enteros). */
  readonly decimales = input(2);
  /** Si acepta un signo menos al inicio. */
  readonly negativos = input(false);

  private readonly campo = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;
  private readonly renderer = inject(Renderer2);
  private alCambiar: (valor: number | null) => void = () => undefined;
  private alTocar: () => void = () => undefined;

  /** @param valor número que llega del modelo; se pinta con punto de miles */
  writeValue(valor: number | string | null | undefined): void {
    const n = typeof valor === 'string' ? Cifra.leer(valor) : valor;
    this.renderer.setProperty(this.campo, 'value', n === null || n === undefined || Number.isNaN(n) ? '' : Cifra.mostrar(n));
  }

  /** @param fn aviso de cambio del formulario */
  registerOnChange(fn: (valor: number | null) => void): void {
    this.alCambiar = fn;
  }

  /** @param fn aviso de que se tocó el campo */
  registerOnTouched(fn: () => void): void {
    this.alTocar = fn;
  }

  /** @param desactivado si el formulario desactiva el campo */
  setDisabledState(desactivado: boolean): void {
    this.renderer.setProperty(this.campo, 'disabled', desactivado);
  }

  /** Limpia lo escrito, pone el punto de miles sin mover el cursor de su cifra y avisa el número. */
  protected alEscribir(): void {
    const crudo = this.campo.value;
    const cursor = this.campo.selectionStart ?? crudo.length;
    const cifrasAntes = crudo.slice(0, cursor).replace(/[^\d,-]/g, '').length;
    const texto = this.limpiar(crudo);
    this.campo.value = texto;
    // el cursor vuelve a quedar después de la misma cantidad de cifras (los puntos no cuentan)
    let posicion = 0;
    for (let vistas = 0; posicion < texto.length && vistas < cifrasAntes; posicion++) {
      if (/[\d,-]/.test(texto[posicion])) {
        vistas++;
      }
    }
    if (document.activeElement === this.campo) {
      this.campo.setSelectionRange(posicion, posicion);
    }
    this.alCambiar(Cifra.leer(texto));
  }

  /** Al salir del campo quita una coma colgando ("12," pasa a "12"). */
  protected alSalir(): void {
    const n = Cifra.leer(this.campo.value);
    this.campo.value = n === null ? '' : Cifra.mostrar(n);
    this.alTocar();
  }

  /**
   * @param crudo lo que hay en el campo
   * @returns texto con punto de miles, a lo sumo una coma y los decimales permitidos
   */
  private limpiar(crudo: string): string {
    const negativo = this.negativos() && crudo.trim().startsWith('-');
    // el punto es siempre de miles (lo pone el campo); el decimal se escribe con coma
    const [enteroCrudo, ...resto] = crudo.replace(/\s/g, '').split(',');
    const entero = enteroCrudo.replace(/\D/g, '').replace(/^0+(?=\d)/, '');
    const decimales = resto.join('').replace(/\D/g, '').slice(0, this.decimales());
    const conPuntos = entero.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    const coma = this.decimales() > 0 && resto.length > 0 ? ',' : '';
    return (negativo ? '-' : '') + conPuntos + coma + decimales;
  }

  /**
   * @param texto cifra con punto de miles y coma decimal
   * @returns el número, o {@code null} si no hay cifras
   */
  static leer(texto: string | null | undefined): number | null {
    if (!texto) {
      return null;
    }
    const negativo = texto.trim().startsWith('-');
    const limpio = texto.replace(/\./g, '').replace(',', '.').replace(/[^\d.]/g, '');
    if (!/\d/.test(limpio)) {
      return null;
    }
    const n = Number(limpio);
    return Number.isNaN(n) ? null : negativo ? -n : n;
  }

  /**
   * @param n número
   * @returns "1.250.000" o "2,5" (sin símbolo de pesos: el campo solo lleva la cifra)
   */
  static mostrar(n: number): string {
    const negativo = n < 0;
    const [entero, decimales] = Math.abs(n).toString().split('.');
    const conPuntos = entero.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    return (negativo ? '-' : '') + conPuntos + (decimales ? ',' + decimales : '');
  }
}
