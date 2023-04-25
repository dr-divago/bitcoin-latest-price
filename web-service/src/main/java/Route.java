public record Route(String route) {
  public static Route of(String route) {
      return new Route(route);
  }
}
