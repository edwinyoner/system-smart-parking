/// Minúsculas y sin tildes, para buscar "san martin" y encontrar "San Martín".
String normalizeText(String value) {
  const from = 'áéíóúüñÁÉÍÓÚÜÑ';
  const to = 'aeiouunaeiouun';
  final buffer = StringBuffer();
  for (final ch in value.trim().split('')) {
    final i = from.indexOf(ch);
    buffer.write(i >= 0 ? to[i] : ch);
  }
  return buffer.toString().toLowerCase();
}
