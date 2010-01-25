package org.mediahub.views

class ViewBinderImpl(registry: ViewRegistry) extends ViewBinder {

  def bindView[A](classifier: ViewClassifier[A]): ViewBindingBuilder[A] =
    new ViewBindingBuilderImpl(registry, classifier)

  def bindView[A, B](classifier: ParamViewClassifier[A, B]): ParamViewBindingBuilder[A, B] =
    new ParamViewBindingBuilderImpl(registry, classifier)

  def install(module: ViewModule) {
    module.configure(this)
  }
}

class ViewBindingBuilderImpl[A](val registry: ViewRegistry, val classifier: Classifier[_]) extends ViewBindingBuilder[A] {
    def of[B](implicit clazz: ClassManifest[B]): TypedViewBindingBuilder[A, B] = 
      new TypedViewBindingBuilderImpl(registry, classifier, clazz.erasure.asInstanceOf[Class[B]])
}

class ClassifiedBindingImpl[A](val clazz: Class[A], val classifier: Classifier[_]) extends ClassifiedBinding[A]

class TypedViewBindingBuilderImpl[A, B](registry: ViewRegistry, 
                                        classifier: Classifier[_],
                                        clazz: Class[B]) extends TypedViewBindingBuilder[A, B] {
  def to(f: (B => A)): ViewBindingRegistration = {
    def binding = new ClassifiedBindingImpl(clazz, classifier) with ViewBinding[B, A] {
      def render(some: B, parent: ViewChain[B, A]): A = f(some)
    }
    registry.register(binding)
  }

  def withParentTo(f: (B, ViewChain[B, A]) => A): ViewBindingRegistration = {
    def binding = new ClassifiedBindingImpl(clazz, classifier) with ViewBinding[B, A] {
      def render(some: B, parent: ViewChain[B, A]): A = f(some, parent)
    }
    registry.register(binding)
  }
}

class ParamViewBindingBuilderImpl[A, B, C](registry: ViewRegistry, classifier: ParamViewClassifier[A, B]) extends ParamViewBindingBuilder[A, B] {
  def of[C](implicit clazz: ClassManifest[C]): TypedParamViewBindingBuilder[A, B, C] =
    new TypedParamViewBindingBuilderImpl(registry, classifier, clazz.erasure.asInstanceOf[Class[C]])
}

class TypedParamViewBindingBuilderImpl[A, B, C](registry: ViewRegistry,
                                                classifier: Classifier[_],
                                                clazz: Class[C])
  extends TypedViewBindingBuilderImpl[A, C](registry, classifier, clazz)
    with TypedParamViewBindingBuilder[A, B, C] {

  def to(f: (C, B) => A): ViewBindingRegistration = {
    def binding = new ClassifiedBindingImpl(clazz, classifier) with ParameterViewBinding[C, B, A] {
      def render(some: C, param: B, parent: ParameterViewChain[C, B, A]): A = f(some, param)
    }
    registry.register(binding)
  }

  def withParentTo(f: (C, B, ParameterViewChain[C, B, A]) => A): ViewBindingRegistration = {
    def binding = new ClassifiedBindingImpl(clazz, classifier) with ParameterViewBinding[C, B, A] {
      def render(some: C, param: B, parent: ParameterViewChain[C, B, A]): A = f(some, param, parent)
    }
    registry.register(binding)
  }
}