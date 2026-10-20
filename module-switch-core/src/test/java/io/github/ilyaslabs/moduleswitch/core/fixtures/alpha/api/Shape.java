package io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api;

/** A sealed DTO hierarchy: not a module API. */
public sealed interface Shape permits Shape.Circle {

    double area();

    record Circle(double radius) implements Shape {
        @Override
        public double area() {
            return Math.PI * this.radius * this.radius;
        }
    }
}
