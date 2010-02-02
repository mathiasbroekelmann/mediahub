package org.mediahub.views

class ViewBinderImpl(registry: ViewRegistry) extends ViewBinder {

  val defaultRanking: Option[Int] = None

  def bindView[A](classifier: ViewClassifier[A]): UnRankedViewBindingBuilder[A] =
    new ViewBindingBuilderImpl(classifier)

  def bindView[A, B](classifier: ParamViewClassifier[A, B]): UnRankedParamViewBindingBuilder[A, B] =
    new ParamViewBindingBuilderImpl(classifier)

  def install(module: ViewModule) {
    module.configure(this)
  }

  def withRanking(ranking: Int) = new ViewBinderImpl(registry) {
    override val defaultRanking = Some(ranking)
  }

  class ViewBindingBuilderImpl[A](val classifier: Classifier[A]) extends UnRankedViewBindingBuilder[A] {
    self =>

    val ranking = defaultRanking

    def of[B](implicit clazz: ClassManifest[B]): TypedViewBindingBuilder[A, B] =
      new TypedViewBindingBuilderImpl(classifier, clazz.erasure.asInstanceOf[Class[B]]) {
        override val ranking = self.ranking
      }

    def withRanking(someRanking: Int): ViewBindingBuilder[A] = new ViewBindingBuilderImpl(classifier) {
      override val ranking = Some(someRanking)
    }

  }

  class ParamViewBindingBuilderImpl[A, B, C](classifier: ParamViewClassifier[A, B]) extends UnRankedParamViewBindingBuilder[A, B] {
    self =>

    val ranking = defaultRanking

    def of[C](implicit clazz: ClassManifest[C]): TypedParamViewBindingBuilder[A, B, C] =
      new TypedParamViewBindingBuilderImpl(classifier, clazz.erasure.asInstanceOf[Class[C]])

    class TypedParamViewBindingBuilderImpl[A, B, C](classifier: ParamViewClassifier[A, B], clazz: Class[C]) 
    extends TypedViewBindingBuilderImpl[A, C](classifier, clazz) with TypedParamViewBindingBuilder[A, B, C] {

      def to(f: (C, B) => A): ViewBindingRegistration = {
        def binding = new ClassifiedBindingImpl(clazz, classifier, ranking) with ParameterViewBinding[C, B, A] {
          def render(some: C, param: B, parent: ParameterViewChain[C, B, A]): A = f(some, param)
        }
        registry.register(binding)
      }

      def withParentTo(f: (C, B, ParameterViewChain[C, B, A]) => A): ViewBindingRegistration = {
        def binding = new ClassifiedBindingImpl(clazz, classifier, ranking) with ParameterViewBinding[C, B, A] {
          def render(some: C, param: B, parent: ParameterViewChain[C, B, A]): A = f(some, param, parent)
        }
        registry.register(binding)
      }
    }

    def withRanking(someRanking: Int): ParamViewBindingBuilder[A, B] = new ParamViewBindingBuilderImpl(classifier) {
      override val ranking = Some(someRanking)
    }

  }

  class TypedViewBindingBuilderImpl[A, B](classifier: Classifier[A], clazz: Class[B]) extends TypedViewBindingBuilder[A, B] {
    self =>

    val ranking: Option[Int] = None

    def to(f: (B => A)): ViewBindingRegistration = {
      def binding = new ClassifiedBindingImpl(clazz, classifier, ranking) with ViewBinding[B, A] {
        def render(some: B, parent: ViewChain[B, A]): A = f(some)
      }
      registry.register(binding)
    }

    def withParentTo(f: (B, ViewChain[B, A]) => A): ViewBindingRegistration = {
      def binding = new ClassifiedBindingImpl(clazz, classifier, ranking) with ViewBinding[B, A] {
        def render(some: B, parent: ViewChain[B, A]): A = f(some, parent)
      }
      registry.register(binding)
    }
  }
}


case class ClassifiedBindingImpl[A](val clazz: Class[A],
                                    val classifier: Classifier[_],
                                    val ranking: Option[Int]) extends ClassifiedBinding[A]
