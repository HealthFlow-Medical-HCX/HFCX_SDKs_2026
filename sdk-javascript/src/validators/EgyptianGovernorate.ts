// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

/**
 * The 27 Egyptian governorates and their National-ID prefix codes.
 * Source: CAPMAS. Cross-SDK invariant — codes match Java's
 * `EgyptianGovernorate`, Python's `EgyptianGovernorate`, and .NET's
 * `EgyptianGovernorate` byte-for-byte.
 */

export interface EgyptianGovernorate {
  readonly code: string;
  readonly name: string;
  readonly englishName: string;
  readonly arabicName: string;
}

const make = (
  code: string,
  name: string,
  englishName: string,
  arabicName: string,
): EgyptianGovernorate => ({ code, name, englishName, arabicName });

export const EgyptianGovernorate = {
  CAIRO: make('01', 'CAIRO', 'Cairo', 'القاهرة'),
  ALEXANDRIA: make('02', 'ALEXANDRIA', 'Alexandria', 'الإسكندرية'),
  PORT_SAID: make('03', 'PORT_SAID', 'Port Said', 'بورسعيد'),
  SUEZ: make('04', 'SUEZ', 'Suez', 'السويس'),
  DAMIETTA: make('11', 'DAMIETTA', 'Damietta', 'دمياط'),
  DAKAHLIA: make('12', 'DAKAHLIA', 'Dakahlia', 'الدقهلية'),
  SHARQIA: make('13', 'SHARQIA', 'Sharqia', 'الشرقية'),
  QALYUBIA: make('14', 'QALYUBIA', 'Qalyubia', 'القليوبية'),
  KAFR_EL_SHEIKH: make('15', 'KAFR_EL_SHEIKH', 'Kafr El Sheikh', 'كفر الشيخ'),
  GHARBIA: make('16', 'GHARBIA', 'Gharbia', 'الغربية'),
  MONUFIA: make('17', 'MONUFIA', 'Monufia', 'المنوفية'),
  BEHEIRA: make('18', 'BEHEIRA', 'Beheira', 'البحيرة'),
  ISMAILIA: make('19', 'ISMAILIA', 'Ismailia', 'الإسماعيلية'),
  GIZA: make('21', 'GIZA', 'Giza', 'الجيزة'),
  BENI_SUEF: make('22', 'BENI_SUEF', 'Beni Suef', 'بني سويف'),
  FAIYUM: make('23', 'FAIYUM', 'Faiyum', 'الفيوم'),
  MINYA: make('24', 'MINYA', 'Minya', 'المنيا'),
  ASYUT: make('25', 'ASYUT', 'Asyut', 'أسيوط'),
  SOHAG: make('26', 'SOHAG', 'Sohag', 'سوهاج'),
  QENA: make('27', 'QENA', 'Qena', 'قنا'),
  ASWAN: make('28', 'ASWAN', 'Aswan', 'أسوان'),
  LUXOR: make('29', 'LUXOR', 'Luxor', 'الأقصر'),
  RED_SEA: make('31', 'RED_SEA', 'Red Sea', 'البحر الأحمر'),
  NEW_VALLEY: make('32', 'NEW_VALLEY', 'New Valley', 'الوادي الجديد'),
  MATRUH: make('33', 'MATRUH', 'Matruh', 'مطروح'),
  NORTH_SINAI: make('34', 'NORTH_SINAI', 'North Sinai', 'شمال سيناء'),
  SOUTH_SINAI: make('35', 'SOUTH_SINAI', 'South Sinai', 'جنوب سيناء'),
} as const;

export const ALL_GOVERNORATES: readonly EgyptianGovernorate[] = Object.values(EgyptianGovernorate);

const _byCode: ReadonlyMap<string, EgyptianGovernorate> = new Map(
  ALL_GOVERNORATES.map((g) => [g.code, g]),
);

/** Look up a governorate by its two-digit National-ID prefix. */
export function egyptianGovernorateFromCode(
  code: string | null | undefined,
): EgyptianGovernorate | undefined {
  if (!code) return undefined;
  return _byCode.get(code);
}
